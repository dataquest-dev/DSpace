/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.itemupdate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.io.file.PathUtils;
import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.authorize.factory.AuthorizeServiceFactory;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.authorize.service.ResourcePolicyService;
import org.dspace.builder.BitstreamBuilder;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.builder.MetadataFieldBuilder;
import org.dspace.builder.ResourcePolicyBuilder;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Collection;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.MetadataField;
import org.dspace.content.MetadataSchema;
import org.dspace.content.MetadataValue;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.BundleService;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.MetadataFieldService;
import org.dspace.content.service.MetadataSchemaService;
import org.dspace.core.Constants;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.Group;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.GroupService;
import org.dspace.handle.factory.HandleServiceFactory;
import org.dspace.handle.service.HandleService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Life cycle of an embargo driven by {@link ItemUpdate}: setting it, re-running the same SAF archive, and
 * ending it with a {@code dc.date.embargoend} in the past, including on policies written by earlier versions.
 * Covers the invariants that one {@code Anonymous}/{@code READ} policy survives every run, that it is mutated
 * rather than recreated, and that the bundles derived from an embargoed file follow it.
 */
public class EmbargoLifecycleIT extends AbstractIntegrationTestWithDatabase {

    /** Target policy name of the fix. Must stay within the 30 char {@code resourcepolicy.rpname} column. */
    private static final String EMBARGO_POLICY_NAME = "embargo";

    /** Policy names written by earlier versions and still present in existing repositories. */
    private static final String LEGACY_STANDARD_EMBARGO = "Standard Embargo";
    private static final String LEGACY_SPECIAL_CASE_EMBARGO = "Special Case Embargo";

    private static final String OPEN_ACCESS = "openAccess";
    private static final String EMBARGOED_ACCESS = "embargoedAccess";

    private static final String TEXT_BUNDLE = "TEXT";
    private static final String THUMBNAIL_BUNDLE = "THUMBNAIL";

    private final ItemService itemService = ContentServiceFactory.getInstance().getItemService();
    private final BundleService bundleService = ContentServiceFactory.getInstance().getBundleService();
    private final HandleService handleService = HandleServiceFactory.getInstance().getHandleService();
    private final ResourcePolicyService resourcePolicyService =
            AuthorizeServiceFactory.getInstance().getResourcePolicyService();
    private final AuthorizeService authorizeService = AuthorizeServiceFactory.getInstance().getAuthorizeService();
    private final GroupService groupService = EPersonServiceFactory.getInstance().getGroupService();
    private final MetadataSchemaService metadataSchemaService =
            ContentServiceFactory.getInstance().getMetadataSchemaService();
    private final MetadataFieldService metadataFieldService =
            ContentServiceFactory.getInstance().getMetadataFieldService();

    private Collection collection;
    private Group anonymousGroup;
    private Path tempDir;
    private String previousHandlePrefix;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context)
                .withName("Parent Community")
                .build();
        collection = CollectionBuilder.createCollection(context, parentCommunity)
                .withName("Collection")
                .build();

        ensureMetadataFieldExists("rights", "access");
        ensureMetadataFieldExists("date", "embargoend");

        anonymousGroup = groupService.findByName(context, Group.ANONYMOUS);
        previousHandlePrefix = ItemUpdate.HANDLE_PREFIX;
        ItemUpdate.HANDLE_PREFIX = handleService.getCanonicalPrefix();

        context.restoreAuthSystemState();

        tempDir = Files.createTempDirectory("embargoLifecycleIT");
    }

    @After
    @Override
    public void destroy() throws Exception {
        ItemUpdate.HANDLE_PREFIX = previousHandlePrefix;
        if (tempDir != null) {
            PathUtils.deleteDirectory(tempDir);
        }
        super.destroy();
    }

    /**
     * Verifies that a SAF package without {@code dc.date.embargoend} leaves every policy as it was. A missing
     * field says nothing about the embargo; an embargo is ended by an end date that lies in the past.
     */
    @Test
    public void removingEmbargoMetadataLeavesPoliciesUntouched() throws Exception {
        String futureEmbargoEnd = LocalDate.now().plusMonths(6).toString();

        Item item = createItem("Lift Embargo Thesis");
        Bitstream bitstream = createOriginalBitstream(item, "thesis.pdf");
        Integer importedPolicyId = onlyAnonymousReadPolicy(bitstream, "the fresh SAF import").getID();

        // the operator embargoes the item
        assertRunSucceeded("setting the embargo", runItemUpdate(item, dublinCore(item, EMBARGOED_ACCESS,
                futureEmbargoEnd)));
        item = context.reloadEntity(item);
        bitstream = context.reloadEntity(bitstream);

        assertEquals("itemupdate did not store the future embargo end date",
                futureEmbargoEnd, singleMetadataValue(item, "date", "embargoend"));
        ResourcePolicy embargoed = onlyAnonymousReadPolicy(bitstream, "the embargo run");
        assertNotNull("the surviving Anonymous READ policy must be dated while the embargo runs",
                embargoed.getStartDate());
        assertEquals("the imported Anonymous READ policy must be mutated in place, not deleted and recreated",
                importedPolicyId, embargoed.getID());
        assertFalse("while embargoed the file must not be publicly readable" + describePolicies(bitstream),
                anonymousCanRead(bitstream));

        Set<String> policiesBefore = policySignatures(bitstream);

        // the next SAF package does not carry dc.date.embargoend
        assertRunSucceeded("running without dc.date.embargoend",
                runItemUpdate(item, dublinCore(item, OPEN_ACCESS, null)));
        item = context.reloadEntity(item);
        bitstream = context.reloadEntity(bitstream);

        assertTrue("fixture precondition: itemupdate has to remove dc.date.embargoend from the item, otherwise"
                        + " syncEmbargoPolicies still sees an end date and this test covers nothing",
                itemService.getMetadata(item, "dc", "date", "embargoend", Item.ANY).isEmpty());

        assertEquals("a SAF package without dc.date.embargoend must leave every resource policy exactly as it"
                        + " was - a changed policy set means an embargo was lifted by the absence of a field"
                        + describePolicies(bitstream),
                policiesBefore, policySignatures(bitstream));
        assertFalse("removing dc.date.embargoend published an embargoed file. Absence of the field is not an"
                        + " instruction; an embargo is ended by a dc.date.embargoend in the past."
                        + describePolicies(bitstream),
                anonymousCanRead(bitstream));
    }

    /**
     * Verifies that an embargo itemupdate never set survives a run without {@code dc.date.embargoend}. The
     * survivor is found by {@code (group, action)}, so a policy from the submission UI or from
     * {@code bulk-access-control} is indistinguishable from one of itemupdate's own.
     */
    @Test
    public void foreignEmbargoIsNeverLifted() throws Exception {
        LocalDate foreignEmbargoStart = LocalDate.now().plusYears(2);

        Item item = createItem("Submission Embargo Thesis");
        Bitstream bitstream = createOriginalBitstream(item, "submission-embargo.pdf");

        // exactly what the submission access condition / bulk-access-control leave behind
        ResourcePolicy foreign = replaceAnonymousReadPolicies(bitstream, startOfDayUtc(foreignEmbargoStart),
                EMBARGO_POLICY_NAME, ResourcePolicy.TYPE_CUSTOM);
        bitstream = context.reloadEntity(bitstream);

        assertEquals("fixture precondition: the bitstream must carry exactly the policy the submission writes",
                ResourcePolicy.TYPE_CUSTOM,
                onlyAnonymousReadPolicy(bitstream, "the fixture").getRpType());
        assertFalse("fixture precondition: the foreign embargo must block anonymous access"
                        + describePolicies(bitstream),
                anonymousCanRead(bitstream));

        Set<String> policiesBefore = policySignatures(bitstream);
        Integer foreignPolicyId = foreign.getID();

        // a routine metadata batch: the fields are targeted, the package carries neither of them
        assertRunSucceeded("running a batch that does not mention the embargo",
                runItemUpdate(item, dublinCore(item, null, null)));
        item = context.reloadEntity(item);
        bitstream = context.reloadEntity(bitstream);

        assertEquals("itemupdate lifted an embargo it never set. The policy was written by the submission"
                        + " access condition or by bulk-access-control and is indistinguishable from one of"
                        + " itemupdate's own, so the absence of dc.date.embargoend must never touch it."
                        + describePolicies(bitstream),
                policiesBefore, policySignatures(bitstream));

        ResourcePolicy survivor = onlyAnonymousReadPolicy(bitstream, "a batch without dc.date.embargoend");
        assertEquals("the foreign policy must still be the very same row", foreignPolicyId, survivor.getID());
        assertNotNull("the foreign embargo start date must survive untouched" + describePolicies(bitstream),
                survivor.getStartDate());
        assertEquals("the foreign embargo start date must not move",
                foreignEmbargoStart, toLocalDate(survivor.getStartDate()));
        assertFalse("a file embargoed outside itemupdate became publicly readable" + describePolicies(bitstream),
                anonymousCanRead(bitstream));
    }

    /**
     * Verifies that re-running the identical SAF archive is a no-op: the same {@code policy_id} comes back,
     * which only holds if the policy is mutated rather than deleted and recreated.
     */
    @Test
    public void syncIsIdempotent() throws Exception {
        String futureEmbargoEnd = LocalDate.now().plusMonths(3).toString();
        LocalDate expectedStart = LocalDate.parse(futureEmbargoEnd).plusDays(1);

        Item item = createItem("Idempotent Thesis");
        Bitstream bitstream = createOriginalBitstream(item, "idempotent.pdf");
        Integer importedPolicyId = onlyAnonymousReadPolicy(bitstream, "the fresh SAF import").getID();

        assertRunSucceeded("setting the embargo",
                runItemUpdate(item, dublinCore(item, EMBARGOED_ACCESS, futureEmbargoEnd)));
        item = context.reloadEntity(item);
        bitstream = context.reloadEntity(bitstream);

        ResourcePolicy first = onlyAnonymousReadPolicy(bitstream, "the first run");
        Integer firstId = first.getID();
        assertEquals("the imported Anonymous READ policy must be mutated in place, not deleted and recreated",
                importedPolicyId, firstId);
        assertNotNull("the surviving policy must be dated after the first run", first.getStartDate());
        assertEquals("embargo must start the day after dc.date.embargoend",
                expectedStart, toLocalDate(first.getStartDate()));
        assertEquals("the surviving policy must be renamed to the canonical access condition",
                EMBARGO_POLICY_NAME, first.getRpName());
        assertEquals("the surviving policy must be TYPE_CUSTOM", ResourcePolicy.TYPE_CUSTOM, first.getRpType());

        // exactly the same archive again
        assertRunSucceeded("re-running the identical archive",
                runItemUpdate(item, dublinCore(item, EMBARGOED_ACCESS, futureEmbargoEnd)));
        item = context.reloadEntity(item);
        bitstream = context.reloadEntity(bitstream);

        ResourcePolicy second = onlyAnonymousReadPolicy(bitstream, "the second, identical run");
        assertEquals("a repeated identical run must mutate the same policy row - a changed policy_id proves"
                        + " the policy was deleted and recreated, which is what loses the file on the way"
                        + describePolicies(bitstream),
                firstId, second.getID());
        assertEquals("a repeated identical run must not move the embargo start date",
                expectedStart, toLocalDate(second.getStartDate()));
        assertEquals("a repeated identical run must not change the policy name",
                EMBARGO_POLICY_NAME, second.getRpName());
        assertEquals("a repeated identical run must not change the policy type",
                ResourcePolicy.TYPE_CUSTOM, second.getRpType());
        assertFalse("the file must still be embargoed after the second run" + describePolicies(bitstream),
                anonymousCanRead(bitstream));
    }

    /**
     * Verifies that a legacy policy whose start date has passed is found by {@code (Anonymous, READ)}, reused
     * and normalised. Looking the survivor up by {@code rpName} would leave the expired policy in place next
     * to a new one, and the file would stay downloadable.
     */
    @Test
    public void legacyRpNameThenFutureEmbargoIsEnforced() throws Exception {
        assertLegacyPolicyIsAdoptedAndEnforced(LEGACY_STANDARD_EMBARGO, EMBARGOED_ACCESS, "legacy-standard.pdf");
    }

    /**
     * Same as {@link #legacyRpNameThenFutureEmbargoIsEnforced()} for the second legacy name, written whenever
     * {@code dc.rights.access} was not {@code embargoedAccess}.
     */
    @Test
    public void legacySpecialCaseRpNameIsAlsoPickedUp() throws Exception {
        assertLegacyPolicyIsAdoptedAndEnforced(LEGACY_SPECIAL_CASE_EMBARGO, null, "legacy-special-case.pdf");
    }

    /**
     * Verifies that embargoing a born-open item consumes its immediate ({@code startDate == null}) policy;
     * left next to a dated one it would keep granting anonymous READ and the embargo would be a no-op.
     */
    @Test
    public void bornOpenItemThenFutureEmbargoIsEnforced() throws Exception {
        String futureEmbargoEnd = LocalDate.now().plusMonths(4).toString();

        Item item = createItem("Born Open Thesis");
        Bitstream bitstream = createOriginalBitstream(item, "born-open.pdf");

        ResourcePolicy imported = onlyAnonymousReadPolicy(bitstream, "the fresh SAF import");
        Integer importedPolicyId = imported.getID();
        assertNull("fixture precondition: a born open bitstream carries an immediate policy",
                imported.getStartDate());
        assertTrue("fixture precondition: a born open bitstream is publicly readable", anonymousCanRead(bitstream));

        assertRunSucceeded("embargoing a born-open item",
                runItemUpdate(item, dublinCore(item, EMBARGOED_ACCESS, futureEmbargoEnd)));
        item = context.reloadEntity(item);
        bitstream = context.reloadEntity(bitstream);

        List<ResourcePolicy> after = anonymousReadPolicies(bitstream);
        assertEquals("the immediate Anonymous READ policy must be consumed by the embargo, not left beside a"
                        + " dated one" + describePolicies(bitstream),
                1, after.size());

        ResourcePolicy survivor = after.get(0);
        assertEquals("the immediate policy must be mutated in place, not deleted and recreated",
                importedPolicyId, survivor.getID());
        assertNotNull("the surviving policy must be dated", survivor.getStartDate());
        assertEquals("embargo must start the day after dc.date.embargoend",
                LocalDate.parse(futureEmbargoEnd).plusDays(1), toLocalDate(survivor.getStartDate()));
        assertEquals("the surviving policy must be renamed to the canonical access condition",
                EMBARGO_POLICY_NAME, survivor.getRpName());
        assertEquals("the surviving policy must be TYPE_CUSTOM",
                ResourcePolicy.TYPE_CUSTOM, survivor.getRpType());
        assertFalse("an immediate Anonymous READ policy left next to the embargo makes the embargo a no-op"
                        + describePolicies(bitstream),
                anonymousCanRead(bitstream));
    }

    /**
     * Verifies that accumulated {@code Anonymous}/{@code READ} policies collapse into one of the pre-existing
     * ones; a second survivor would either defeat the embargo or resurrect an obsolete date.
     */
    @Test
    public void duplicateAnonymousReadPoliciesCollapseToOne() throws Exception {
        String futureEmbargoEnd = LocalDate.now().plusMonths(5).toString();

        Item item = createItem("Duplicate Policies Thesis");
        Bitstream bitstream = createOriginalBitstream(item, "duplicates.pdf");

        Integer immediateId = onlyAnonymousReadPolicy(bitstream, "the fresh SAF import").getID();
        Integer oldestDatedId = addAnonymousReadPolicy(bitstream, startOfDayUtc(LocalDate.now().minusMonths(3)),
                LEGACY_STANDARD_EMBARGO).getID();
        Integer newestDatedId = addAnonymousReadPolicy(bitstream, startOfDayUtc(LocalDate.now().plusMonths(2)),
                LEGACY_SPECIAL_CASE_EMBARGO).getID();
        bitstream = context.reloadEntity(bitstream);
        assertEquals("fixture precondition: three Anonymous READ policies" + describePolicies(bitstream),
                3, anonymousReadPolicies(bitstream).size());

        assertRunSucceeded("collapsing duplicate Anonymous READ policies",
                runItemUpdate(item, dublinCore(item, EMBARGOED_ACCESS, futureEmbargoEnd)));
        item = context.reloadEntity(item);
        bitstream = context.reloadEntity(bitstream);

        List<ResourcePolicy> after = anonymousReadPolicies(bitstream);
        assertEquals("duplicate Anonymous READ policies must collapse into exactly one"
                        + describePolicies(bitstream),
                1, after.size());

        ResourcePolicy survivor = after.get(0);
        Integer survivorId = survivor.getID();
        assertTrue("the survivor must be one of the three pre-existing policies - a brand new policy_id proves"
                        + " delete and recreate" + describePolicies(bitstream),
                survivorId.equals(immediateId) || survivorId.equals(oldestDatedId)
                        || survivorId.equals(newestDatedId));
        assertTrue("the survivor must be the oldest policy: either the immediate one (in force since forever)"
                        + " or the one with the oldest start date - never the newest one"
                        + describePolicies(bitstream),
                survivorId.equals(immediateId) || survivorId.equals(oldestDatedId));
        assertEquals("embargo must start the day after dc.date.embargoend",
                LocalDate.parse(futureEmbargoEnd).plusDays(1), toLocalDate(survivor.getStartDate()));
        assertEquals("the surviving policy must be renamed to the canonical access condition",
                EMBARGO_POLICY_NAME, survivor.getRpName());
        assertFalse("a second Anonymous READ policy would defeat the embargo" + describePolicies(bitstream),
                anonymousCanRead(bitstream));
    }

    /**
     * Verifies that an ORIGINAL bitstream which had a READ policy before a run still has one afterwards,
     * whatever {@code dc.date.embargoend} contained. Each case first puts a real embargo in place, which is
     * what replaces the collection default with a single dated policy.
     */
    @Test
    public void neverZeroReadPoliciesInvariant() throws Exception {
        String initialEmbargoEnd = LocalDate.now().plusYears(1).toString();
        String impossibleCalendarDay = LocalDate.now().plusYears(1).getYear() + "-02-30";

        List<String[]> cases = new ArrayList<>();
        // { label, dc.rights.access, dc.date.embargoend (null = element absent from the SAF) }
        cases.add(new String[] { "future date", OPEN_ACCESS, LocalDate.now().plusMonths(9).toString() });
        cases.add(new String[] { "today", OPEN_ACCESS, LocalDate.now().toString() });
        cases.add(new String[] { "past date", OPEN_ACCESS, LocalDate.now().minusMonths(1).toString() });
        cases.add(new String[] { "empty value", OPEN_ACCESS, "" });
        cases.add(new String[] { "unparseable value", OPEN_ACCESS, "not-a-date" });
        cases.add(new String[] { "impossible calendar day", OPEN_ACCESS, impossibleCalendarDay });
        cases.add(new String[] { "element removed", OPEN_ACCESS, null });

        StringBuilder violations = new StringBuilder();

        for (String[] testCase : cases) {
            String label = testCase[0];

            Item item = createItem("Invariant Thesis - " + label);
            Bitstream bitstream = createOriginalBitstream(item, "invariant.pdf");

            runItemUpdate(item, dublinCore(item, EMBARGOED_ACCESS, initialEmbargoEnd));
            item = context.reloadEntity(item);
            bitstream = context.reloadEntity(bitstream);

            int readPoliciesBefore = readPolicies(bitstream).size();
            int anonymousBefore = anonymousReadPolicies(bitstream).size();
            if (readPoliciesBefore < 1 || anonymousBefore < 1) {
                violations.append(System.lineSeparator())
                        .append("  [").append(label).append("] the fixture was already broken by the embargo run:")
                        .append(describePolicies(bitstream));
                continue;
            }

            runItemUpdate(item, dublinCore(item, testCase[1], testCase[2]));
            item = context.reloadEntity(item);
            bitstream = context.reloadEntity(bitstream);

            int readPoliciesAfter = readPolicies(bitstream).size();
            int anonymousAfter = anonymousReadPolicies(bitstream).size();
            if (readPoliciesAfter < 1 || anonymousAfter < 1) {
                violations.append(System.lineSeparator())
                        .append("  [").append(label).append("] dc.date.embargoend=")
                        .append(testCase[2] == null ? "<element removed>" : "'" + testCase[2] + "'")
                        .append(" reduced ").append(readPoliciesBefore).append(" READ policies (")
                        .append(anonymousBefore).append(" of them Anonymous) to ").append(readPoliciesAfter)
                        .append(" (").append(anonymousAfter).append(" Anonymous):")
                        .append(describePolicies(bitstream));
            }
        }

        if (violations.length() > 0) {
            fail("An itemupdate run must never leave an ORIGINAL bitstream without an Anonymous READ policy."
                    + " A bitstream with no READ policy answers HTTP 401 and no later run can publish it again."
                    + violations);
        }
    }

    /**
     * Verifies that every ORIGINAL bitstream of a record reaches the same state, the primary one included, and
     * that the primary bitstream flag survives.
     */
    @Test
    public void multipleBitstreamsAllGetSameState() throws Exception {
        String futureEmbargoEnd = LocalDate.now().plusMonths(7).toString();
        String pastEmbargoEnd = LocalDate.now().minusMonths(1).toString();

        Item item = createItem("Six File Thesis");
        List<Bitstream> bitstreams = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            bitstreams.add(createOriginalBitstream(item, "file-" + i + ".pdf"));
        }
        Bundle originalBundle = bundleOf(item, Constants.CONTENT_BUNDLE_NAME);
        setPrimaryBitstream(originalBundle, bitstreams.get(0));
        UUID primaryBitstreamId = bitstreams.get(0).getID();

        // embargo every file of the record
        assertRunSucceeded("embargoing every ORIGINAL bitstream",
                runItemUpdate(item, dublinCore(item, EMBARGOED_ACCESS, futureEmbargoEnd)));
        item = context.reloadEntity(item);
        reloadAll(bitstreams);
        originalBundle = context.reloadEntity(originalBundle);

        assertUniformState(bitstreams, "the embargo run");
        for (Bitstream bitstream : bitstreams) {
            ResourcePolicy policy = onlyAnonymousReadPolicy(bitstream, "the embargo run");
            assertEquals("embargo must start the day after dc.date.embargoend",
                    LocalDate.parse(futureEmbargoEnd).plusDays(1), toLocalDate(policy.getStartDate()));
            assertEquals("every ORIGINAL bitstream must carry the canonical policy name",
                    EMBARGO_POLICY_NAME, policy.getRpName());
            assertEquals("every ORIGINAL bitstream must carry a TYPE_CUSTOM policy",
                    ResourcePolicy.TYPE_CUSTOM, policy.getRpType());
            assertFalse("every embargoed file of the record must be closed" + describePolicies(bitstream),
                    anonymousCanRead(bitstream));
        }
        assertNotNull("the ORIGINAL bundle lost its primary bitstream", originalBundle.getPrimaryBitstream());
        assertEquals("the primary bitstream flag must survive an embargo run",
                primaryBitstreamId, originalBundle.getPrimaryBitstream().getID());

        // the embargo expires: the same archive is re-imported with a past date
        assertRunSucceeded("letting the embargo expire",
                runItemUpdate(item, dublinCore(item, OPEN_ACCESS, pastEmbargoEnd)));
        item = context.reloadEntity(item);
        reloadAll(bitstreams);
        originalBundle = context.reloadEntity(originalBundle);

        assertUniformState(bitstreams, "the expired embargo run");
        for (Bitstream bitstream : bitstreams) {
            ResourcePolicy policy = onlyAnonymousReadPolicy(bitstream, "the expired embargo run");
            assertEquals("an expired embargo still starts the day after dc.date.embargoend",
                    LocalDate.parse(pastEmbargoEnd).plusDays(1), toLocalDate(policy.getStartDate()));
            assertTrue("an expired embargo must be effective immediately" + describePolicies(bitstream),
                    resourcePolicyService.isDateValid(policy));
            assertTrue("an expired embargo publishes every file of the record" + describePolicies(bitstream),
                    anonymousCanRead(bitstream));
        }
        assertNotNull("the ORIGINAL bundle lost its primary bitstream", originalBundle.getPrimaryBitstream());
        assertEquals("the primary bitstream flag must survive an expired embargo run",
                primaryBitstreamId, originalBundle.getPrimaryBitstream().getID());
    }

    /**
     * Verifies that the derived bundles follow the embargo of the file they were derived from: the thumbnail
     * and the extracted full text of an embargoed file must not be readable, and both re-open with it. The
     * bundle objects themselves stay out of scope, only their bitstreams are synchronised.
     */
    @Test
    public void derivativeBundlesFollowTheEmbargo() throws Exception {
        LocalDate futureEmbargoEnd = LocalDate.now().plusMonths(2);
        LocalDate pastEmbargoEnd = LocalDate.now().minusMonths(2);

        Item item = createItem("Derivatives Thesis");
        Bitstream original = createOriginalBitstream(item, "thesis.pdf");
        Bitstream extractedText = createBitstreamInBundle(item, "thesis.pdf.txt", TEXT_BUNDLE);
        Bitstream thumbnail = createBitstreamInBundle(item, "thesis.pdf.jpg", THUMBNAIL_BUNDLE);
        List<Bitstream> files = new ArrayList<>(List.of(original, extractedText, thumbnail));

        Bundle originalBundle = bundleOf(item, Constants.CONTENT_BUNDLE_NAME);
        Set<String> originalBundlePolicies = policySignatures(originalBundle);
        assertFalse("fixture precondition: the ORIGINAL bundle must start with policies",
                originalBundlePolicies.isEmpty());
        List<Integer> policyIdsBefore = new ArrayList<>();
        for (Bitstream file : files) {
            policyIdsBefore.add(onlyAnonymousReadPolicy(file, "the fixture setup").getID());
        }

        // embargo run
        assertRunSucceeded("embargoing the item",
                runItemUpdate(item, dublinCore(item, EMBARGOED_ACCESS, futureEmbargoEnd.toString())));
        item = context.reloadEntity(item);
        reloadAll(files);
        originalBundle = context.reloadEntity(originalBundle);

        for (int i = 0; i < files.size(); i++) {
            Bitstream file = files.get(i);
            ResourcePolicy policy = onlyAnonymousReadPolicy(file, "the embargo run");
            assertEquals("the Anonymous READ policy was recreated instead of being re-dated"
                            + describePolicies(file),
                    policyIdsBefore.get(i), policy.getID());
            assertEquals("every bundle derived from the embargoed file carries the item embargo"
                            + describePolicies(file),
                    EMBARGO_POLICY_NAME, policy.getRpName());
            assertEquals("wrong embargo start date" + describePolicies(file),
                    futureEmbargoEnd.plusDays(1), toLocalDate(policy.getStartDate()));
            assertFalse("a file of an item under embargo must not be publicly readable"
                            + describePolicies(file),
                    anonymousCanRead(file));
        }
        assertEquals("the ORIGINAL bundle's own policies must not be touched",
                originalBundlePolicies, policySignatures(originalBundle));

        // expired embargo run
        assertRunSucceeded("letting the embargo expire",
                runItemUpdate(item, dublinCore(item, OPEN_ACCESS, pastEmbargoEnd.toString())));
        item = context.reloadEntity(item);
        reloadAll(files);
        originalBundle = context.reloadEntity(originalBundle);

        for (int i = 0; i < files.size(); i++) {
            Bitstream file = files.get(i);
            ResourcePolicy policy = onlyAnonymousReadPolicy(file, "the expired embargo run");
            assertEquals("the Anonymous READ policy was recreated instead of being re-dated"
                            + describePolicies(file),
                    policyIdsBefore.get(i), policy.getID());
            assertEquals("wrong start date after an expired embargo" + describePolicies(file),
                    pastEmbargoEnd.plusDays(1), toLocalDate(policy.getStartDate()));
            assertTrue("an expired embargo publishes the file together with everything derived from it"
                            + describePolicies(file),
                    anonymousCanRead(file));
        }
        assertEquals("the ORIGINAL bundle's own policies must survive an expired embargo untouched",
                originalBundlePolicies, policySignatures(originalBundle));
    }

    /**
     * Shared body of the two legacy {@code rpName} tests: a bitstream whose only {@code Anonymous}/{@code READ}
     * policy carries a legacy name and a start date that has passed is put back under embargo.
     */
    private void assertLegacyPolicyIsAdoptedAndEnforced(String legacyName, String rightsAccess, String fileName)
            throws Exception {
        String futureEmbargoEnd = LocalDate.now().plusYears(1).toString();

        Item item = createItem("Legacy Policy Thesis - " + legacyName);
        Bitstream bitstream = createOriginalBitstream(item, fileName);
        Integer legacyPolicyId =
                replaceAnonymousReadPolicies(bitstream, startOfDayUtc(LocalDate.now().minusMonths(2)), legacyName)
                        .getID();
        bitstream = context.reloadEntity(bitstream);

        ResourcePolicy legacy = onlyAnonymousReadPolicy(bitstream, "the legacy fixture");
        assertEquals("fixture precondition: the legacy policy must be the only Anonymous READ policy",
                legacyPolicyId, legacy.getID());
        assertEquals("fixture precondition: the legacy policy keeps its old name", legacyName, legacy.getRpName());
        assertTrue("fixture precondition: an expired legacy embargo leaves the file public"
                        + describePolicies(bitstream),
                anonymousCanRead(bitstream));

        assertRunSucceeded("adopting a legacy embargo policy",
                runItemUpdate(item, dublinCore(item, rightsAccess, futureEmbargoEnd)));
        item = context.reloadEntity(item);
        bitstream = context.reloadEntity(bitstream);

        List<ResourcePolicy> after = anonymousReadPolicies(bitstream);
        assertEquals("the legacy policy must be adopted, so exactly one Anonymous READ policy may remain."
                        + " Looking the survivor up by rpName instead of by (Anonymous, READ) leaves the expired"
                        + " legacy policy next to the new one and the embargo never takes effect."
                        + describePolicies(bitstream),
                1, after.size());

        ResourcePolicy survivor = after.get(0);
        assertEquals("the legacy policy must be mutated in place, not deleted and recreated",
                legacyPolicyId, survivor.getID());
        assertEquals("the legacy policy name must be normalised", EMBARGO_POLICY_NAME, survivor.getRpName());
        assertEquals("the normalised policy must be TYPE_CUSTOM",
                ResourcePolicy.TYPE_CUSTOM, survivor.getRpType());
        assertNotNull("the normalised policy must be dated", survivor.getStartDate());
        assertEquals("embargo must start the day after dc.date.embargoend",
                LocalDate.parse(futureEmbargoEnd).plusDays(1), toLocalDate(survivor.getStartDate()));
        assertFalse("re-embargoing a legacy bitstream must actually block anonymous download"
                        + describePolicies(bitstream),
                anonymousCanRead(bitstream));
    }

    /**
     * Asserts that every bitstream of the record ended in the same state - same policy count, same start date,
     * same name, same type, same answer to "may an anonymous visitor download it".
     */
    private void assertUniformState(List<Bitstream> bitstreams, String what) throws Exception {
        String expected = stateSignature(bitstreams.get(0));
        for (Bitstream bitstream : bitstreams) {
            assertEquals("all ORIGINAL bitstreams of a record must share the same state after " + what
                            + " (bitstream " + bitstream.getID() + ")",
                    expected, stateSignature(bitstream));
        }
    }

    /**
     * Tells whether a visitor who is not logged in may read the bitstream.
     */
    private boolean anonymousCanRead(Bitstream bitstream) throws Exception {
        EPerson saved = context.getCurrentUser();
        int popped = 0;
        while (context.ignoreAuthorization()) {
            context.restoreAuthSystemState();
            popped++;
        }
        context.setCurrentUser(null);
        try {
            return authorizeService.authorizeActionBoolean(context, bitstream, Constants.READ);
        } finally {
            context.setCurrentUser(saved);
            for (int i = 0; i < popped; i++) {
                context.turnOffAuthorisationSystem();
            }
        }
    }

    private List<ResourcePolicy> readPolicies(Bitstream bitstream) throws Exception {
        return resourcePolicyService.find(context, bitstream, Constants.READ);
    }

    private List<ResourcePolicy> anonymousReadPolicies(Bitstream bitstream) throws Exception {
        return readPolicies(bitstream).stream()
                .filter(policy -> policy.getGroup() != null && anonymousGroup.equals(policy.getGroup()))
                .collect(Collectors.toList());
    }

    private ResourcePolicy onlyAnonymousReadPolicy(Bitstream bitstream, String what) throws Exception {
        List<ResourcePolicy> policies = anonymousReadPolicies(bitstream);
        assertEquals("exactly one Anonymous READ policy must remain after " + what + describePolicies(bitstream),
                1, policies.size());
        return policies.get(0);
    }

    /**
     * State of a bitstream with the policy identities left out, so two different bitstreams can be compared.
     */
    private String stateSignature(Bitstream bitstream) throws Exception {
        StringBuilder sb = new StringBuilder();
        List<ResourcePolicy> policies = anonymousReadPolicies(bitstream);
        sb.append("anonymousReadPolicies=").append(policies.size());
        for (ResourcePolicy policy : policies) {
            sb.append(" [start=")
                    .append(policy.getStartDate() == null ? "null" : toLocalDate(policy.getStartDate()))
                    .append(" end=").append(policy.getEndDate() == null ? "null" : toLocalDate(policy.getEndDate()))
                    .append(" rpName=").append(policy.getRpName())
                    .append(" rpType=").append(policy.getRpType())
                    .append(" valid=").append(resourcePolicyService.isDateValid(policy))
                    .append(']');
        }
        sb.append(" anonymousCanRead=").append(anonymousCanRead(bitstream));
        return sb.toString();
    }

    /**
     * Full identity of every policy of a DSpace object, {@code policy_id} included - used to prove that a set
     * of policies was not touched at all.
     */
    private Set<String> policySignatures(DSpaceObject dso) throws Exception {
        Set<String> signatures = new TreeSet<>();
        for (ResourcePolicy policy : resourcePolicyService.find(context, dso)) {
            signatures.add(String.format("id=%s action=%s group=%s eperson=%s start=%s end=%s rpName=%s rpType=%s",
                    policy.getID(),
                    Constants.actionText[policy.getAction()],
                    policy.getGroup() == null ? "<none>" : policy.getGroup().getName(),
                    policy.getEPerson() == null ? "<none>" : policy.getEPerson().getEmail(),
                    policy.getStartDate(),
                    policy.getEndDate(),
                    policy.getRpName(),
                    policy.getRpType()));
        }
        return signatures;
    }

    private String describePolicies(Bitstream bitstream) throws Exception {
        StringBuilder sb = new StringBuilder(System.lineSeparator());
        sb.append("      bitstream=").append(bitstream.getID()).append(System.lineSeparator())
                .append("      anonymousCanRead=").append(anonymousCanRead(bitstream))
                .append(System.lineSeparator());

        List<ResourcePolicy> policies = readPolicies(bitstream);
        if (policies.isEmpty()) {
            sb.append("      <NO READ POLICIES AT ALL>").append(System.lineSeparator());
        }
        for (ResourcePolicy policy : policies) {
            sb.append(String.format("      id=%s group=%s action=%s rpType=%s rpName=%s start=%s end=%s valid=%s",
                            policy.getID(),
                            policy.getGroup() == null ? "<none>" : policy.getGroup().getName(),
                            Constants.actionText[policy.getAction()],
                            policy.getRpType(),
                            policy.getRpName(),
                            policy.getStartDate(),
                            policy.getEndDate(),
                            resourcePolicyService.isDateValid(policy)))
                    .append(System.lineSeparator());
        }
        return sb.toString();
    }

    private void ensureMetadataFieldExists(String element, String qualifier) throws Exception {
        MetadataSchema dcSchema = metadataSchemaService.find(context, "dc");
        MetadataField existingField = metadataFieldService.findByElement(context, dcSchema, element, qualifier);
        if (existingField == null) {
            MetadataFieldBuilder.createMetadataField(context, dcSchema, element, qualifier, null).build();
        }
    }

    private Item createItem(String title) throws Exception {
        context.turnOffAuthorisationSystem();
        Item item = ItemBuilder.createItem(context, collection)
                .withTitle(title)
                .build();
        context.restoreAuthSystemState();
        return item;
    }

    /**
     * Creates a bitstream in the ORIGINAL bundle. It inherits the collection DEFAULT_BITSTREAM_READ and so
     * carries one undated Anonymous READ policy, the state a freshly imported SAF package is in.
     */
    private Bitstream createOriginalBitstream(Item item, String name) throws Exception {
        context.turnOffAuthorisationSystem();
        Bitstream bitstream = BitstreamBuilder.createBitstream(context, item,
                        new ByteArrayInputStream(("content-" + name).getBytes(StandardCharsets.UTF_8)))
                .withName(name)
                .withMimeType("text/plain")
                .build();
        context.restoreAuthSystemState();
        return bitstream;
    }

    private Bitstream createBitstreamInBundle(Item item, String name, String bundleName) throws Exception {
        context.turnOffAuthorisationSystem();
        Bitstream bitstream = BitstreamBuilder.createBitstream(context, item,
                        new ByteArrayInputStream(("content-" + name).getBytes(StandardCharsets.UTF_8)), bundleName)
                .withName(name)
                .withMimeType("text/plain")
                .build();
        context.restoreAuthSystemState();
        return bitstream;
    }

    private Bundle bundleOf(Item item, String bundleName) throws Exception {
        List<Bundle> bundles = itemService.getBundles(item, bundleName);
        assertFalse("fixture precondition: the item must have a " + bundleName + " bundle", bundles.isEmpty());
        return bundles.get(0);
    }

    private void setPrimaryBitstream(Bundle bundle, Bitstream bitstream) throws Exception {
        context.turnOffAuthorisationSystem();
        bundle.setPrimaryBitstreamID(bitstream);
        bundleService.update(context, bundle);
        context.restoreAuthSystemState();
    }

    private void reloadAll(List<Bitstream> bitstreams) throws Exception {
        for (int i = 0; i < bitstreams.size(); i++) {
            bitstreams.set(i, context.reloadEntity(bitstreams.get(i)));
        }
    }

    private ResourcePolicy addAnonymousReadPolicy(Bitstream bitstream, Date startDate, String name)
            throws Exception {
        return addAnonymousReadPolicy(bitstream, startDate, name, null);
    }

    private ResourcePolicy addAnonymousReadPolicy(Bitstream bitstream, Date startDate, String name,
                                                  String policyType) throws Exception {
        context.turnOffAuthorisationSystem();
        ResourcePolicyBuilder builder = ResourcePolicyBuilder.createResourcePolicy(context, null, anonymousGroup)
                .withAction(Constants.READ)
                .withDspaceObject(bitstream)
                .withName(name);
        if (startDate != null) {
            builder.withStartDate(startDate);
        }
        if (policyType != null) {
            builder.withPolicyType(policyType);
        }
        ResourcePolicy policy = builder.build();
        context.restoreAuthSystemState();
        return policy;
    }

    /**
     * Replaces every READ policy of the bitstream with a single Anonymous READ policy, the state earlier
     * versions left behind.
     */
    private ResourcePolicy replaceAnonymousReadPolicies(Bitstream bitstream, Date startDate, String name)
            throws Exception {
        return replaceAnonymousReadPolicies(bitstream, startDate, name, null);
    }

    private ResourcePolicy replaceAnonymousReadPolicies(Bitstream bitstream, Date startDate, String name,
                                                       String policyType) throws Exception {
        context.turnOffAuthorisationSystem();
        authorizeService.removePoliciesActionFilter(context, bitstream, Constants.READ);
        context.restoreAuthSystemState();
        return addAnonymousReadPolicy(bitstream, startDate, name, policyType);
    }

    private String singleMetadataValue(Item item, String element, String qualifier) {
        List<MetadataValue> values = itemService.getMetadata(item, "dc", element, qualifier, Item.ANY);
        return values.isEmpty() ? null : values.get(0).getValue();
    }

    private Date startOfDayUtc(LocalDate day) {
        return Date.from(day.atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    private LocalDate toLocalDate(Date date) {
        if (date instanceof java.sql.Date) {
            return ((java.sql.Date) date).toLocalDate();
        }
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    /**
     * Runs itemupdate with both embargo fields as targets, the combination that triggers embargo
     * synchronisation.
     *
     * @return the number of embargo problems the run reported, which is what {@code ItemUpdate.main()} turns
     *         into a non-zero exit code
     */
    private int runItemUpdate(Item item, String dublinCoreContent) throws Exception {
        Path sourceRoot = Files.createDirectory(tempDir.resolve("saf-" + System.nanoTime()));
        Files.createFile(sourceRoot.resolve(ItemUpdate.SUPPRESS_UNDO_FILENAME));

        Path itemDir = Files.createDirectory(sourceRoot.resolve("item_000"));
        Files.writeString(itemDir.resolve("dublin_core.xml"), dublinCoreContent, StandardCharsets.UTF_8);

        ItemUpdate itemUpdate = new ItemUpdate();
        DeleteMetadataAction deleteAction =
                (DeleteMetadataAction) itemUpdate.actionMgr.getUpdateAction(DeleteMetadataAction.class);
        deleteAction.addTargetFields(new String[] { "dc.rights.access", "dc.date.embargoend" });

        AddMetadataAction addAction =
                (AddMetadataAction) itemUpdate.actionMgr.getUpdateAction(AddMetadataAction.class);
        addAction.addTargetFields(new String[] { "dc.rights.access", "dc.date.embargoend" });

        context.turnOffAuthorisationSystem();
        itemUpdate.processArchive(context, sourceRoot.toString(), null, null, true, false, true);
        context.restoreAuthSystemState();

        context.uncacheEntity(item);
        return itemUpdate.embargoSyncFailures;
    }

    /**
     * A run the tool is supposed to carry out has to end with exit code 0.
     */
    private void assertRunSucceeded(String what, int embargoSyncFailures) {
        assertEquals("itemupdate reported an embargo synchronisation problem while " + what + ", so"
                        + " ItemUpdate.main() would exit with " + ItemUpdate.exitStatus(0, embargoSyncFailures)
                        + " although nothing was wrong with the input",
                0, embargoSyncFailures);
    }

    /**
     * Builds a SAF {@code dublin_core.xml}. A {@code null} value omits the element entirely (that is how an
     * operator removes a field), an empty string is written as a single space because an empty XML element is
     * dropped by the parser.
     */
    private String dublinCore(Item item, String rightsAccess, String embargoEndDate) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<dublin_core schema=\"dc\">\n")
                .append("    <dcvalue element=\"identifier\" qualifier=\"uri\">")
                .append(ItemUpdate.HANDLE_PREFIX).append(item.getHandle())
                .append("</dcvalue>\n");

        if (rightsAccess != null) {
            sb.append("    <dcvalue element=\"rights\" qualifier=\"access\">")
                    .append(rightsAccess)
                    .append("</dcvalue>\n");
        }

        if (embargoEndDate != null) {
            sb.append("    <dcvalue element=\"date\" qualifier=\"embargoend\">")
                    .append(embargoEndDate.isEmpty() ? " " : embargoEndDate)
                    .append("</dcvalue>\n");
        }

        sb.append("</dublin_core>");
        return sb.toString();
    }
}

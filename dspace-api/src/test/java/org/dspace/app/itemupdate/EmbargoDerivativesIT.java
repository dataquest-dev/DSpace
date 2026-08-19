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
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
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
import org.dspace.content.Item;
import org.dspace.content.MetadataField;
import org.dspace.content.MetadataSchema;
import org.dspace.content.factory.ContentServiceFactory;
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
 * Embargo synchronisation of the bundles derived from an embargoed file. While an item is under embargo
 * neither its extracted full text (TEXT) nor its thumbnail (THUMBNAIL) may be readable, and both have to be
 * re-opened when the embargo ends. Bundles that are not derived from the file, LICENSE above all, stay out
 * of scope.
 */
public class EmbargoDerivativesIT extends AbstractIntegrationTestWithDatabase {

    /** Policy name the synchronisation writes. */
    private static final String EMBARGO_POLICY_NAME = "embargo";

    private static final String OPEN_ACCESS = "openAccess";
    private static final String EMBARGOED_ACCESS = "embargoedAccess";
    private static final String RESTRICTED_ACCESS = "restrictedAccess";

    private static final String TEXT_BUNDLE = "TEXT";
    private static final String THUMBNAIL_BUNDLE = "THUMBNAIL";
    private static final String LICENSE_BUNDLE = "LICENSE";
    private static final String CC_LICENSE_BUNDLE = "CC-LICENSE";

    private final ItemService itemService = ContentServiceFactory.getInstance().getItemService();
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

        // neither field exists in the test metadata registry, AddMetadataAction needs both
        ensureMetadataFieldExists("rights", "access");
        ensureMetadataFieldExists("date", "embargoend");

        anonymousGroup = groupService.findByName(context, Group.ANONYMOUS);
        // ItemArchive resolves items through this mutable static, it is restored in destroy()
        previousHandlePrefix = ItemUpdate.HANDLE_PREFIX;
        ItemUpdate.HANDLE_PREFIX = handleService.getCanonicalPrefix();

        context.restoreAuthSystemState();

        tempDir = Files.createTempDirectory("embargoDerivativesIT");
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
     * Verifies that a running embargo closes the thumbnail and the extracted full text as well. The TEXT
     * bundle holds the whole content of the file, so leaving it public publishes the embargoed work.
     */
    @Test
    public void futureEmbargoAlsoClosesTextAndThumbnail() throws Exception {
        LocalDate embargoEnd = LocalDate.now().plusMonths(6);
        LocalDate accessStart = embargoEnd.plusDays(1);

        Item item = createItem("Embargoed Derivatives Thesis");
        List<Bitstream> files = createOriginalWithDerivatives(item, "thesis.pdf");

        for (Bitstream file : files) {
            assertTrue("fixture precondition: [" + label(file) + "] must start publicly readable"
                            + describe(file),
                    anonymousCanRead(file));
        }

        Run run = runItemUpdate(item, dublinCore(item, EMBARGOED_ACCESS, embargoEnd.toString()));
        item = context.reloadEntity(item);
        reloadAll(files);

        assertExitCode("future embargo on an item with derivatives", 0, run);
        for (Bitstream file : files) {
            ResourcePolicy policy = onlyAnonymousReadPolicy(file, "the embargo run");
            assertNotNull("[" + label(file) + "] the surviving Anonymous READ policy carries no start date, so"
                            + " the embargo was never written onto this bundle." + describe(file),
                    policy.getStartDate());
            assertEquals("[" + label(file) + "] the embargo belongs to the item, so every bundle derived from"
                            + " the embargoed file has to carry the same start date." + describe(file),
                    accessStart, toLocalDate(policy.getStartDate()));
            assertFalse("[" + label(file) + "] is publicly readable while the item is under embargo until "
                            + embargoEnd + ". Neither the thumbnail nor the extracted full text of an"
                            + " embargoed file may be reachable." + describe(file),
                    anonymousCanRead(file));
        }
    }

    /**
     * Verifies that an expired embargo re-opens the derivatives too. Closing them is only half the job; a
     * published thesis whose thumbnail stays hidden is just as wrong.
     */
    @Test
    public void expiredEmbargoReopensTextAndThumbnail() throws Exception {
        LocalDate embargoEnd = LocalDate.now().minusMonths(2);
        LocalDate accessStart = embargoEnd.plusDays(1);

        Item item = createItem("Expired Derivatives Thesis");
        List<Bitstream> files = createOriginalWithDerivatives(item, "expired.pdf");

        // the state a finished embargo run leaves behind: one dated Anonymous READ policy on every bundle
        for (Bitstream file : files) {
            replaceAnonymousReadPolicy(file, startOfDayUtc(LocalDate.now().plusYears(1)), EMBARGO_POLICY_NAME);
        }
        reloadAll(files);
        for (Bitstream file : files) {
            assertFalse("fixture precondition: [" + label(file) + "] must start closed" + describe(file),
                    anonymousCanRead(file));
        }

        Run run = runItemUpdate(item, dublinCore(item, OPEN_ACCESS, embargoEnd.toString()));
        item = context.reloadEntity(item);
        reloadAll(files);

        assertExitCode("expired embargo on an item with derivatives", 0, run);
        for (Bitstream file : files) {
            ResourcePolicy policy = onlyAnonymousReadPolicy(file, "the expired embargo run");
            assertNotNull("[" + label(file) + "] the Anonymous READ policy lost its start date instead of being"
                            + " re-dated to the day the embargo ended." + describe(file),
                    policy.getStartDate());
            assertEquals("[" + label(file) + "] wrong start date after an embargo that ended on " + embargoEnd
                            + describe(file),
                    accessStart, toLocalDate(policy.getStartDate()));
            assertTrue("[" + label(file) + "] is still closed although the embargo ended on " + embargoEnd
                            + ". An expired embargo publishes the file together with everything derived from"
                            + " it." + describe(file),
                    anonymousCanRead(file));
        }
    }

    /**
     * Verifies that the derivative policy is re-dated in place, the rule the ORIGINAL bundle already follows:
     * a delete plus create leaves the bitstream without any policy if the run breaks in between.
     */
    @Test
    public void derivativePolicyIsMutatedNotRecreated() throws Exception {
        LocalDate embargoEnd = LocalDate.now().plusMonths(3);
        LocalDate accessStart = embargoEnd.plusDays(1);

        Item item = createItem("Mutated Derivatives Thesis");
        List<Bitstream> files = createOriginalWithDerivatives(item, "mutate.pdf");
        List<Snapshot> before = snapshotAll(files);

        Run run = runItemUpdate(item, dublinCore(item, EMBARGOED_ACCESS, embargoEnd.toString()));
        item = context.reloadEntity(item);
        reloadAll(files);

        assertExitCode("future embargo on an item with derivatives", 0, run);
        for (int i = 0; i < files.size(); i++) {
            Bitstream file = files.get(i);
            ResourcePolicy policy = onlyAnonymousReadPolicy(file, "the embargo run");
            assertEquals("[" + label(file) + "] the set of policy ids changed, so the Anonymous READ policy was"
                            + " deleted and recreated instead of being re-dated." + describe(file),
                    before.get(i).ids, policyIds(file));
            assertNotNull("[" + label(file) + "] the policy was kept but never re-dated, so this bundle stayed"
                            + " outside the embargo." + describe(file),
                    policy.getStartDate());
            assertEquals("[" + label(file) + "] wrong embargo start date" + describe(file),
                    accessStart, toLocalDate(policy.getStartDate()));
            assertEquals("[" + label(file) + "] the mutated policy has to be named '" + EMBARGO_POLICY_NAME
                            + "', otherwise the next run cannot tell it apart from a foreign one."
                            + describe(file),
                    EMBARGO_POLICY_NAME, policy.getRpName());
        }
    }

    /**
     * Verifies that the scope stops at the derived bundles. A licence is not derived from the embargoed file
     * and has to stay readable, whatever the embargo end date says.
     */
    @Test
    public void licenseBundleIsNeverTouched() throws Exception {
        LocalDate futureEnd = LocalDate.now().plusMonths(4);
        LocalDate pastEnd = LocalDate.now().minusMonths(4);

        Item item = createItem("Licensed Thesis");
        Bitstream original = createBitstreamInBundle(item, "licensed.pdf", Constants.CONTENT_BUNDLE_NAME);
        List<Bitstream> licences = new ArrayList<>();
        licences.add(createBitstreamInBundle(item, "license.txt", LICENSE_BUNDLE));
        licences.add(createBitstreamInBundle(item, "license_rdf", CC_LICENSE_BUNDLE));
        List<Snapshot> before = snapshotAll(licences);

        Run embargoRun = runItemUpdate(item, dublinCore(item, EMBARGOED_ACCESS, futureEnd.toString()));
        item = context.reloadEntity(item);
        original = context.reloadEntity(original);
        reloadAll(licences);

        assertExitCode("future embargo on an item with licence bundles", 0, embargoRun);
        assertFalse("sanity check: this run has to embargo the ORIGINAL bitstream, otherwise nothing happened"
                        + " at all and the licence bundles are untested." + describe(original),
                anonymousCanRead(original));
        assertUntouched("future embargo end date", before, licences);

        Run expiredRun = runItemUpdate(item, dublinCore(item, OPEN_ACCESS, pastEnd.toString()));
        item = context.reloadEntity(item);
        original = context.reloadEntity(original);
        reloadAll(licences);

        assertExitCode("expired embargo on an item with licence bundles", 0, expiredRun);
        assertTrue("sanity check: the expired embargo has to publish the ORIGINAL bitstream"
                        + describe(original),
                anonymousCanRead(original));
        assertUntouched("expired embargo end date", before, licences);
        for (Bitstream licence : licences) {
            assertTrue("[" + label(licence) + "] the licence text must stay publicly readable"
                            + describe(licence),
                    anonymousCanRead(licence));
        }
    }

    /**
     * Verifies that a derivative without an {@code Anonymous}/{@code READ} policy does not get one. Creating
     * it would grant access nobody granted, so the run reports the bitstream instead.
     */
    @Test
    public void derivativeWithoutAnonymousReadIsNotPublished() throws Exception {
        LocalDate embargoEnd = LocalDate.now().plusMonths(5);

        Item item = createItem("Closed Text Thesis");
        Bitstream original = createBitstreamInBundle(item, "closed.pdf", Constants.CONTENT_BUNDLE_NAME);
        Bitstream text = createBitstreamInBundle(item, "closed.pdf.txt", TEXT_BUNDLE);

        context.turnOffAuthorisationSystem();
        deleteReadPolicies(text);
        context.restoreAuthSystemState();
        text = context.reloadEntity(text);

        assertTrue("fixture precondition: the TEXT bitstream must carry no READ policy" + describe(text),
                readPolicies(text).isEmpty());

        Run run = runItemUpdate(item, dublinCore(item, EMBARGOED_ACCESS, embargoEnd.toString()));
        item = context.reloadEntity(item);
        original = context.reloadEntity(original);
        text = context.reloadEntity(text);

        assertTrue("[TEXT] a READ policy was created for a bitstream that had none. Synchronising an embargo"
                        + " re-dates existing access, it never grants access nobody granted." + describe(text),
                readPolicies(text).isEmpty());
        assertFalse("[TEXT] became publicly readable although it carried no policy before" + describe(text),
                anonymousCanRead(text));
        assertTrue("the run has to report the TEXT bitstream " + text.getID() + " it could not synchronise,"
                        + " otherwise the operator never learns that the extracted text was left behind."
                        + " Console output was:" + System.lineSeparator() + run.console,
                run.console.contains(text.getID().toString()));
        assertFalse("a derivative that cannot be synchronised must not stop the ORIGINAL bitstream from being"
                        + " embargoed" + describe(original),
                anonymousCanRead(original));
    }

    /**
     * Verifies that the guards which keep itemupdate away from an item cover the derived bundles too: an
     * access right the tool does not manage and a withdrawn item both leave every policy where it was.
     */
    @Test
    public void guardsAlsoProtectDerivatives() throws Exception {
        Item restricted = createItem("Restricted Derivatives Thesis");
        List<Bitstream> restrictedFiles = createOriginalWithDerivatives(restricted, "restricted.pdf");
        List<Snapshot> restrictedBefore = snapshotAll(restrictedFiles);

        for (String embargoEnd : Arrays.asList(LocalDate.now().plusYears(1).toString(),
                LocalDate.now().minusYears(1).toString())) {
            Run run = runItemUpdate(restricted, dublinCore(restricted, RESTRICTED_ACCESS, embargoEnd));
            restricted = context.reloadEntity(restricted);
            reloadAll(restrictedFiles);

            assertTrue("itemupdate has to refuse the item because of dc.rights.access=" + RESTRICTED_ACCESS
                            + ". Nothing in the console output says so, so some other guard stopped the run."
                            + " Console output was:" + System.lineSeparator() + run.console,
                    run.console.contains(RESTRICTED_ACCESS));
            assertUntouched(RESTRICTED_ACCESS + " with embargo end " + embargoEnd, restrictedBefore,
                    restrictedFiles);
        }

        Item withdrawn = createItem("Withdrawn Derivatives Thesis");
        List<Bitstream> withdrawnFiles = createOriginalWithDerivatives(withdrawn, "withdrawn.pdf");

        context.turnOffAuthorisationSystem();
        itemService.withdraw(context, withdrawn);
        context.restoreAuthSystemState();
        withdrawn = context.reloadEntity(withdrawn);
        reloadAll(withdrawnFiles);

        assertTrue("fixture precondition: the item must be withdrawn", withdrawn.isWithdrawn());
        List<Snapshot> withdrawnBefore = snapshotAll(withdrawnFiles);

        Run run = runItemUpdate(withdrawn,
                dublinCore(withdrawn, EMBARGOED_ACCESS, LocalDate.now().plusYears(1).toString()));
        withdrawn = context.reloadEntity(withdrawn);
        reloadAll(withdrawnFiles);

        assertTrue("itemupdate has to refuse the item because it is withdrawn. Console output was:"
                        + System.lineSeparator() + run.console,
                run.console.contains("is withdrawn"));
        assertUntouched("withdrawn item with a future embargo end date", withdrawnBefore, withdrawnFiles);
        for (Bitstream file : withdrawnFiles) {
            assertFalse("[" + label(file) + "] a withdrawn file must not be publicly readable, the takedown"
                            + " covers everything derived from it." + describe(file),
                    anonymousCanRead(file));
        }
    }

    /**
     * Compares policy identity (ids) and policy content (fingerprints) of every bitstream: the ids catch
     * delete-and-recreate, the fingerprints catch in-place mutation.
     */
    private void assertUntouched(String scenario, List<Snapshot> before, List<Bitstream> bitstreams)
            throws SQLException {
        for (int i = 0; i < bitstreams.size(); i++) {
            Bitstream bitstream = bitstreams.get(i);
            assertEquals("[" + scenario + "][" + label(bitstream) + "] the set of resource policy ids changed:"
                            + " policies were deleted and/or recreated although this bundle is out of scope."
                            + describe(bitstream),
                    before.get(i).ids, policyIds(bitstream));
            assertEquals("[" + scenario + "][" + label(bitstream) + "] a surviving resource policy was modified"
                            + " in place although this bundle is out of scope." + describe(bitstream),
                    before.get(i).fingerprints, policyFingerprints(bitstream));
        }
    }

    /**
     * Runs itemupdate with both embargo fields as targets, the combination that triggers embargo
     * synchronisation.
     *
     * @return the console output of the run and the number of embargo problems it counted
     */
    private Run runItemUpdate(Item item, String dublinCoreContent) throws Exception {
        Path sourceRoot = Files.createDirectory(tempDir.resolve("saf-" + System.nanoTime()));
        // without this marker processArchive writes an undo archive next to the source directory
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

        // ItemUpdate reports to the console only, so it has to be captured to assert on it
        ByteArrayOutputStream consoleBuffer = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        PrintStream captureStream = new PrintStream(consoleBuffer, true, StandardCharsets.UTF_8);

        context.turnOffAuthorisationSystem();
        System.setOut(captureStream);
        System.setErr(captureStream);
        try {
            itemUpdate.processArchive(context, sourceRoot.toString(), null, null, true, false, true);
        } finally {
            captureStream.flush();
            System.setOut(originalOut);
            System.setErr(originalErr);
            context.restoreAuthSystemState();
        }

        context.uncacheEntity(item);

        String consoleOutput = consoleBuffer.toString(StandardCharsets.UTF_8);
        // replay it so the failsafe -output.txt still holds the full ItemUpdate log
        System.out.println(consoleOutput);
        return new Run(consoleOutput, itemUpdate.embargoSyncFailures);
    }

    /**
     * What a finished {@code itemupdate} run is judged by: its console output and its failure count.
     */
    private static final class Run {
        private final String console;
        private final int embargoSyncFailures;

        private Run(String console, int embargoSyncFailures) {
            this.console = console;
            this.embargoSyncFailures = embargoSyncFailures;
        }
    }

    /**
     * Identity and content of every policy of one bitstream, taken before a run.
     */
    private static final class Snapshot {
        private final Set<Integer> ids;
        private final List<String> fingerprints;

        private Snapshot(Set<Integer> ids, List<String> fingerprints) {
            this.ids = ids;
            this.fingerprints = fingerprints;
        }
    }

    private List<Snapshot> snapshotAll(List<Bitstream> bitstreams) throws SQLException {
        List<Snapshot> snapshots = new ArrayList<>();
        for (Bitstream bitstream : bitstreams) {
            snapshots.add(new Snapshot(policyIds(bitstream), policyFingerprints(bitstream)));
        }
        return snapshots;
    }

    /**
     * Asserts the exit code the run would have produced; without it a skipped item looks synchronised.
     */
    private void assertExitCode(String scenario, int expectedFailures, Run run) {
        assertEquals("[" + scenario + "] wrong number of reported embargo problems, so ItemUpdate.main() would"
                        + " exit with " + ItemUpdate.exitStatus(0, run.embargoSyncFailures) + " instead of "
                        + ItemUpdate.exitStatus(0, expectedFailures) + ". Console output was:"
                        + System.lineSeparator() + run.console,
                expectedFailures, run.embargoSyncFailures);
    }

    /**
     * Builds a SAF {@code dublin_core.xml} carrying one {@code dc.rights.access} and one
     * {@code dc.date.embargoend} value.
     */
    private String dublinCore(Item item, String accessRight, String embargoEndDate) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<dublin_core schema=\"dc\">\n"
                + "    <dcvalue element=\"identifier\" qualifier=\"uri\">"
                + ItemUpdate.HANDLE_PREFIX + item.getHandle() + "</dcvalue>\n"
                + "    <dcvalue element=\"rights\" qualifier=\"access\">" + accessRight + "</dcvalue>\n"
                + "    <dcvalue element=\"date\" qualifier=\"embargoend\">" + embargoEndDate + "</dcvalue>\n"
                + "</dublin_core>";
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
     * The state filter-media leaves behind on a public item: the file plus its extracted text and its
     * thumbnail, each with the one undated Anonymous READ policy inherited from the collection.
     */
    private List<Bitstream> createOriginalWithDerivatives(Item item, String fileName) throws Exception {
        List<Bitstream> files = new ArrayList<>();
        files.add(createBitstreamInBundle(item, fileName, Constants.CONTENT_BUNDLE_NAME));
        files.add(createBitstreamInBundle(item, fileName + ".txt", TEXT_BUNDLE));
        files.add(createBitstreamInBundle(item, fileName + ".jpg", THUMBNAIL_BUNDLE));
        return files;
    }

    private Bitstream createBitstreamInBundle(Item item, String name, String bundleName) throws Exception {
        context.turnOffAuthorisationSystem();
        Bitstream bitstream = BitstreamBuilder.createBitstream(context, item,
                        new ByteArrayInputStream(("content-" + name).getBytes(StandardCharsets.UTF_8)),
                        bundleName)
                .withName(name)
                .withMimeType("text/plain")
                .build();
        context.restoreAuthSystemState();
        return bitstream;
    }

    /**
     * Replaces every READ policy of the bitstream with a single dated Anonymous READ policy.
     */
    private ResourcePolicy replaceAnonymousReadPolicy(Bitstream bitstream, Date startDate, String name)
            throws Exception {
        context.turnOffAuthorisationSystem();
        deleteReadPolicies(bitstream);
        ResourcePolicy policy = ResourcePolicyBuilder.createResourcePolicy(context, null, anonymousGroup)
                .withAction(Constants.READ)
                .withDspaceObject(bitstream)
                .withName(name)
                .withPolicyType(ResourcePolicy.TYPE_CUSTOM)
                .withStartDate(startDate)
                .build();
        context.restoreAuthSystemState();
        return policy;
    }

    /**
     * Deletes READ policies one by one, because the bulk removal helpers issue an HQL delete and leave the
     * in-memory collection stale.
     */
    private void deleteReadPolicies(Bitstream bitstream) throws Exception {
        for (ResourcePolicy policy : readPolicies(bitstream)) {
            resourcePolicyService.delete(context, policy);
        }
    }

    private List<ResourcePolicy> readPolicies(Bitstream bitstream) throws SQLException {
        return new ArrayList<>(resourcePolicyService.find(context, bitstream, Constants.READ));
    }

    private List<ResourcePolicy> anonymousReadPolicies(Bitstream bitstream) throws SQLException {
        return readPolicies(bitstream).stream()
                .filter(policy -> policy.getGroup() != null && anonymousGroup.equals(policy.getGroup()))
                .collect(Collectors.toList());
    }

    private ResourcePolicy onlyAnonymousReadPolicy(Bitstream bitstream, String what) throws SQLException {
        List<ResourcePolicy> policies = anonymousReadPolicies(bitstream);
        assertEquals("[" + label(bitstream) + "] exactly one Anonymous READ policy must remain after " + what
                        + ", a second one would defeat the embargo." + describe(bitstream),
                1, policies.size());
        return policies.get(0);
    }

    private Set<Integer> policyIds(Bitstream bitstream) throws SQLException {
        Set<Integer> ids = new TreeSet<>();
        for (ResourcePolicy policy : resourcePolicyService.find(context, bitstream)) {
            ids.add(policy.getID());
        }
        return ids;
    }

    private List<String> policyFingerprints(Bitstream bitstream) throws SQLException {
        List<String> fingerprints = new ArrayList<>();
        for (ResourcePolicy policy : resourcePolicyService.find(context, bitstream)) {
            fingerprints.add(fingerprint(policy));
        }
        Collections.sort(fingerprints);
        return fingerprints;
    }

    private String fingerprint(ResourcePolicy policy) {
        return String.format("id=%s action=%s group=%s eperson=%s rpType=%s rpName=%s start=%s end=%s",
                policy.getID(),
                Constants.actionText[policy.getAction()],
                policy.getGroup() == null ? "<none>" : policy.getGroup().getName(),
                policy.getEPerson() == null ? "<none>" : policy.getEPerson().getEmail(),
                policy.getRpType(),
                policy.getRpName(),
                day(policy.getStartDate()),
                day(policy.getEndDate()));
    }

    /**
     * Renders the current policies of the bitstream for failure messages, so a red build shows which policy
     * moved.
     */
    private String describe(Bitstream bitstream) throws SQLException {
        StringBuilder sb = new StringBuilder(System.lineSeparator())
                .append("  bitstream=").append(label(bitstream)).append(" (").append(bitstream.getID())
                .append(")").append(System.lineSeparator())
                .append("      anonymousCanRead=").append(anonymousCanRead(bitstream))
                .append(System.lineSeparator());
        List<String> fingerprints = policyFingerprints(bitstream);
        if (fingerprints.isEmpty()) {
            sb.append("      <NO RESOURCE POLICIES AT ALL>").append(System.lineSeparator());
        }
        for (String fingerprint : fingerprints) {
            sb.append("      ").append(fingerprint).append(System.lineSeparator());
        }
        return sb.toString();
    }

    /**
     * Bundle and file name of a bitstream, so a failure message says which bundle is wrong.
     */
    private String label(Bitstream bitstream) throws SQLException {
        List<Bundle> bundles = bitstream.getBundles();
        return (bundles.isEmpty() ? "<no bundle>" : bundles.get(0).getName()) + "/" + bitstream.getName();
    }

    /**
     * Tells whether a visitor who is not logged in may read the bitstream. The authorisation state is a stack
     * the builders push and pop, so it is drained first - otherwise every read looks allowed.
     */
    private boolean anonymousCanRead(Bitstream bitstream) throws SQLException {
        EPerson savedUser = context.getCurrentUser();
        int popped = 0;
        while (context.ignoreAuthorization()) {
            context.restoreAuthSystemState();
            popped++;
        }
        context.setCurrentUser(null);
        try {
            return authorizeService.authorizeActionBoolean(context, bitstream, Constants.READ);
        } finally {
            context.setCurrentUser(savedUser);
            for (int i = 0; i < popped; i++) {
                context.turnOffAuthorisationSystem();
            }
        }
    }

    private void reloadAll(List<Bitstream> bitstreams) throws SQLException {
        for (int i = 0; i < bitstreams.size(); i++) {
            bitstreams.set(i, context.reloadEntity(bitstreams.get(i)));
        }
    }

    private Date startOfDayUtc(LocalDate day) {
        return Date.from(day.atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    /**
     * Start dates come back from the database as {@code java.sql.Date}, so they are compared at calendar day
     * granularity instead of as instants.
     */
    private LocalDate toLocalDate(Date date) {
        if (date instanceof java.sql.Date) {
            return ((java.sql.Date) date).toLocalDate();
        }
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private String day(Date date) {
        return date == null ? "<none>" : toLocalDate(date).toString();
    }
}

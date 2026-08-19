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
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.authorize.factory.AuthorizeServiceFactory;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.authorize.service.ResourcePolicyService;
import org.dspace.builder.BitstreamBuilder;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.GroupBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.builder.MetadataFieldBuilder;
import org.dspace.builder.ResourcePolicyBuilder;
import org.dspace.content.Bitstream;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.MetadataField;
import org.dspace.content.MetadataSchema;
import org.dspace.content.MetadataValue;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.MetadataFieldService;
import org.dspace.content.service.MetadataSchemaService;
import org.dspace.core.Constants;
import org.dspace.core.Context;
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
 * Pins down the cases in which embargo synchronisation in {@link ItemUpdate} has to leave a bitstream alone
 * rather than publish it. Every "must not touch" assertion compares policy ids and a fingerprint of every
 * policy, because counts hide both delete-and-recreate and in-place mutation.
 */
public class EmbargoSafetyIT extends AbstractIntegrationTestWithDatabase {

    /**
     * rpName written by earlier versions; the fixtures use it so that normalisation of legacy policies is
     * exercised.
     */
    private static final String LEGACY_EMBARGO_POLICY_NAME = "Standard Embargo";

    /**
     * The supported way of re-opening files whose Anonymous READ policy is already gone; ItemUpdate points
     * the operator at it instead of inventing a public policy.
     */
    private static final String BULK_ACCESS_CONTROL_HINT = "bulk-access-control";

    /**
     * Access condition writing an {@code Anonymous}/{@code READ} policy with an END date: public now, closed
     * again on that day.
     */
    private static final String LEASE_POLICY_NAME = "lease";

    /**
     * Sentinel for {@link #deletePolicies(Bitstream, int)} meaning "every action", picked so it can never
     * collide with a real value of {@link Constants#actionText}.
     */
    private static final int ALL_ACTIONS = -1;

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

        tempDir = Files.createTempDirectory("embargoSafetyIT");
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
     * Verifies that a withdrawn item is left alone: withdrawal turns every READ policy into WITHDRAWN_READ,
     * and synchronising an embargo must not undo a takedown.
     */
    @Test
    public void withdrawnItemIsNeverRepublished() throws Exception {
        Item item = createItem("Withdrawn thesis");
        Bitstream bitstream = createEmbargoedBitstream(item, "withdrawn.pdf");

        context.turnOffAuthorisationSystem();
        itemService.withdraw(context, item);
        context.restoreAuthSystemState();

        item = context.reloadEntity(item);
        bitstream = context.reloadEntity(bitstream);

        assertTrue("fixture precondition: the item must be withdrawn", item.isWithdrawn());
        assertFalse("fixture precondition: withdrawal must leave WITHDRAWN_READ policies behind"
                        + describe(bitstream),
                policiesForAction(bitstream, Constants.WITHDRAWN_READ).isEmpty());
        assertTrue("fixture precondition: a withdrawn bitstream must carry no READ policy" + describe(bitstream),
                policiesForAction(bitstream, Constants.READ).isEmpty());
        assertFalse("fixture precondition: a withdrawn file must not be publicly readable" + describe(bitstream),
                anonymousCanRead(bitstream));

        Set<Integer> idsBefore = policyIds(bitstream);
        List<String> policiesBefore = policyFingerprints(bitstream);

        Run pastRun = runItemUpdate(item, dublinCore(item, Collections.singletonList("openAccess"), pastDate()));

        item = context.reloadEntity(item);
        bitstream = context.reloadEntity(bitstream);

        // withdraw() also clears archived, so without this check the !isArchived guard alone would satisfy
        // every policy assertion below.
        assertTrue("ItemUpdate has to refuse a withdrawn item because it is withdrawn. Nothing in the console"
                        + " output says so, so some other guard stopped the run and the withdrawal guard is"
                        + " untested. Console output was:" + System.lineSeparator() + pastRun.console,
                pastRun.console.contains("is withdrawn"));
        assertExitCode("withdrawn item, expired embargo", 0, pastRun);
        assertTrue("An expired embargo on a WITHDRAWN item created an action=READ policy."
                        + " Withdrawal must never be undone by itemupdate, only WITHDRAWN_READ may remain."
                        + describe(bitstream),
                policiesForAction(bitstream, Constants.READ).isEmpty());
        assertUntouched("withdrawn item", idsBefore, policiesBefore, bitstream);
        assertFalse("A withdrawn file became publicly readable after an expired embargo was synchronised."
                        + describe(bitstream),
                anonymousCanRead(bitstream));

        // The past-date run above only reaches the early return; the future-date branch is the one that
        // creates policies.
        Run futureRun =
                runItemUpdate(item, dublinCore(item, Collections.singletonList("embargoedAccess"), futureDate()));

        item = context.reloadEntity(item);
        bitstream = context.reloadEntity(bitstream);

        assertTrue("ItemUpdate has to refuse a withdrawn item because it is withdrawn. Console output was:"
                        + System.lineSeparator() + futureRun.console,
                futureRun.console.contains("is withdrawn"));
        assertExitCode("withdrawn item, future embargo", 0, futureRun);
        assertTrue("A FUTURE dc.date.embargoend on a WITHDRAWN item created an action=READ policy. A withdrawn"
                        + " item must never gain one - only WITHDRAWN_READ may remain - otherwise the takedown"
                        + " undoes itself the moment the embargo lapses." + describe(bitstream),
                policiesForAction(bitstream, Constants.READ).isEmpty());
        assertUntouched("withdrawn item with a future embargo end date", idsBefore, policiesBefore, bitstream);
        assertFalse("A withdrawn file became publicly readable after a future embargo end date was synchronised."
                        + describe(bitstream),
                anonymousCanRead(bitstream));
    }

    /**
     * Verifies that {@code restrictedAccess} keeps the files closed whatever the embargo end date says.
     */
    @Test
    public void restrictedAccessWithStaleEmbargoEndIsUntouched() throws Exception {
        assertEmbargoSyncIsANoOp("dc.rights.access=restrictedAccess with a past embargo end",
                Collections.singletonList("restrictedAccess"), pastDate(), 0);
    }

    /**
     * Verifies that {@code metadataOnlyAccess} keeps the bitstreams undisclosed.
     */
    @Test
    public void metadataOnlyAccessIsUntouched() throws Exception {
        assertEmbargoSyncIsANoOp("dc.rights.access=metadataOnlyAccess with a past embargo end",
                Collections.singletonList("metadataOnlyAccess"), pastDate(), 0);
    }

    /**
     * Verifies that an access right the tool does not understand means "hands off" rather than "open".
     */
    @Test
    public void unknownAccessRightValueIsUntouched() throws Exception {
        assertEmbargoSyncIsANoOp("dc.rights.access=someAccessRightWeDoNotKnow with a past embargo end",
                Collections.singletonList("someAccessRightWeDoNotKnow"), pastDate(), 0);
    }

    /**
     * Verifies that one value outside the allowlist blocks the whole item, even next to {@code openAccess}:
     * contradictory metadata is not resolved in favour of disclosure.
     */
    @Test
    public void mixedAccessRightsWithOneDisallowedIsUntouched() throws Exception {
        assertEmbargoSyncIsANoOp("dc.rights.access=openAccess + restrictedAccess with a past embargo end",
                Arrays.asList("openAccess", "restrictedAccess"), pastDate(), 0);
    }

    /**
     * Verifies that a bitstream readable by a named group only gains no Anonymous policy: with nothing to
     * mutate, creating one would widen access nobody granted.
     */
    @Test
    public void bitstreamWithoutAnonymousReadIsNotPublished() throws Exception {
        Item item = createItem("Group restricted thesis");
        Bitstream bitstream = createOriginalBitstream(item, "group-restricted.pdf");

        context.turnOffAuthorisationSystem();
        Group reviewers = GroupBuilder.createGroup(context)
                .withName("Thesis reviewers " + System.nanoTime())
                .build();
        deletePolicies(bitstream, Constants.READ);
        ResourcePolicyBuilder.createResourcePolicy(context, null, reviewers)
                .withAction(Constants.READ)
                .withDspaceObject(bitstream)
                .withName("Reviewers only")
                .build();
        context.restoreAuthSystemState();

        bitstream = context.reloadEntity(bitstream);

        assertTrue("fixture precondition: no Anonymous READ policy may be left on the bitstream"
                + describe(bitstream), anonymousReadPolicies(bitstream).isEmpty());
        assertFalse("fixture precondition: a group restricted file must not be publicly readable"
                + describe(bitstream), anonymousCanRead(bitstream));

        Set<Integer> idsBefore = policyIds(bitstream);
        List<String> policiesBefore = policyFingerprints(bitstream);

        Run pastRun = runItemUpdate(item, dublinCore(item, Collections.singletonList("openAccess"), pastDate()));

        bitstream = context.reloadEntity(bitstream);

        assertTrue("An expired embargo created an Anonymous READ policy on a bitstream that only ever granted"
                        + " READ to a named group. Access may be re-dated, never widened."
                        + describe(bitstream),
                anonymousReadPolicies(bitstream).isEmpty());
        assertUntouched("bitstream readable by a named group only", idsBefore, policiesBefore, bitstream);
        assertFalse("A group restricted file became publicly readable after an expired embargo was synchronised."
                + describe(bitstream), anonymousCanRead(bitstream));

        // A past date only reaches the early return; the future-date branch is where a group-restricted file
        // could gain an Anonymous policy.
        Run futureRun = runItemUpdate(item,
                dublinCore(item, Collections.singletonList("embargoedAccess"), futureDate()));

        bitstream = context.reloadEntity(bitstream);

        assertTrue("A FUTURE dc.date.embargoend created an Anonymous READ policy on a bitstream that only ever"
                        + " granted READ to a named group. The embargo would lapse into public access nobody"
                        + " ever granted." + describe(bitstream),
                anonymousReadPolicies(bitstream).isEmpty());
        assertUntouched("bitstream readable by a named group only, future embargo end date",
                idsBefore, policiesBefore, bitstream);
        assertFalse("A group restricted file became publicly readable after a future embargo end date was"
                + " synchronised." + describe(bitstream), anonymousCanRead(bitstream));

        assertTrue("There is no Anonymous READ policy to re-date here, so ItemUpdate has to report the bitstream"
                        + " it could not synchronise and name '" + BULK_ACCESS_CONTROL_HINT + "' as the supported"
                        + " way to change access. Console output of the expired-embargo run was:"
                        + System.lineSeparator() + pastRun.console,
                pastRun.console.contains(BULK_ACCESS_CONTROL_HINT));
        assertTrue("ItemUpdate stayed silent about a bitstream it could not synchronise. Console output of the"
                        + " future-embargo run was:" + System.lineSeparator() + futureRun.console,
                futureRun.console.contains(BULK_ACCESS_CONTROL_HINT));

        // An unsynchronised bitstream that leaves the exit code at 0 is invisible to the caller.
        assertExitCode("bitstream without Anonymous READ, expired embargo", 1, pastRun);
        assertExitCode("bitstream without Anonymous READ, future embargo", 1, futureRun);
    }

    /**
     * Verifies that a bitstream left without any resource policy stays that way: what it used to grant cannot
     * be reconstructed, so the run reports the damage instead of guessing.
     */
    @Test
    public void alreadyBrokenBitstreamWithZeroPoliciesStaysZero() throws Exception {
        Item item = createItem("Already broken thesis");
        Bitstream bitstream = createOriginalBitstream(item, "already-broken.pdf");

        context.turnOffAuthorisationSystem();
        deletePolicies(bitstream, ALL_ACTIONS);
        context.restoreAuthSystemState();

        bitstream = context.reloadEntity(bitstream);

        assertTrue("fixture precondition: the bitstream must carry no resource policy at all"
                + describe(bitstream), allPolicies(bitstream).isEmpty());
        assertFalse("fixture precondition: a bitstream without policies must not be readable"
                + describe(bitstream), anonymousCanRead(bitstream));

        Run run = runItemUpdate(item, dublinCore(item, Collections.singletonList("openAccess"), pastDate()));

        bitstream = context.reloadEntity(bitstream);

        assertTrue("ItemUpdate invented resource policies for a bitstream that had none. A bitstream with zero"
                        + " policies carries no evidence of who was allowed to read it, so re-creating a policy is"
                        + " a guess - and the only wrong guess leaks the file."
                        + describe(bitstream),
                allPolicies(bitstream).isEmpty());
        assertFalse("A bitstream with zero policies became publicly readable." + describe(bitstream),
                anonymousCanRead(bitstream));
        assertTrue("ItemUpdate stayed silent about a bitstream it could not synchronise. It has to report the"
                        + " failure and name '" + BULK_ACCESS_CONTROL_HINT + "' as the supported way to restore"
                        + " access. Console output was:\n" + run.console,
                run.console.contains(BULK_ACCESS_CONTROL_HINT));
        assertExitCode("bitstream with zero policies", 1, run);
    }

    /**
     * Verifies that an {@code Anonymous}/{@code READ} policy with an END date is left alone. It is a lease
     * ("public now, closed again on that day"), which {@code dc.date.embargoend} says nothing about.
     */
    @Test
    public void leasedAnonymousReadPolicyIsUntouched() throws Exception {
        Item item = createItem("Leased thesis");
        Bitstream bitstream = createLeasedBitstream(item, "leased.pdf");

        assertTrue("fixture precondition: a lease that has not expired yet makes the file publicly readable"
                + describe(bitstream), anonymousCanRead(bitstream));

        Set<Integer> idsBefore = policyIds(bitstream);
        List<String> policiesBefore = policyFingerprints(bitstream);

        // The future-date branch is the one that writes policies, so it is where the end date would be lost.
        Run futureRun = runItemUpdate(item,
                dublinCore(item, Collections.singletonList("embargoedAccess"), futureDate()));

        bitstream = context.reloadEntity(bitstream);

        assertUntouched("leased Anonymous READ policy, future embargo end", idsBefore, policiesBefore, bitstream);
        assertTrue("ItemUpdate has to name '" + BULK_ACCESS_CONTROL_HINT + "' as the supported way to change an"
                        + " access condition it does not manage. Console output was:" + System.lineSeparator()
                        + futureRun.console,
                futureRun.console.contains(BULK_ACCESS_CONTROL_HINT));
        assertExitCode("leased Anonymous READ policy, future embargo end", 1, futureRun);

        // An expired end date reaches the same mutation, only with a start date that has already passed.
        Run pastRun = runItemUpdate(item,
                dublinCore(item, Collections.singletonList("openAccess"), pastDate()));

        bitstream = context.reloadEntity(bitstream);

        assertUntouched("leased Anonymous READ policy, expired embargo end", idsBefore, policiesBefore, bitstream);
        assertExitCode("leased Anonymous READ policy, expired embargo end", 1, pastRun);
    }

    /**
     * Verifies that a lease sitting next to a dated embargo policy is not deleted: the refusal has to consider
     * every {@code Anonymous}/{@code READ} policy, not only the one that would be mutated.
     */
    @Test
    public void leaseNextToADatedEmbargoPolicyIsNotDeleted() throws Exception {
        Item item = createItem("Leased and embargoed thesis");
        Bitstream bitstream = createEmbargoedBitstream(item, "leased-and-embargoed.pdf");

        context.turnOffAuthorisationSystem();
        addLeasePolicy(bitstream);
        context.restoreAuthSystemState();
        bitstream = context.reloadEntity(bitstream);

        assertEquals("fixture precondition: the bitstream has to carry the dated embargo policy AND the lease"
                + describe(bitstream), 2, anonymousReadPolicies(bitstream).size());

        Set<Integer> idsBefore = policyIds(bitstream);
        List<String> policiesBefore = policyFingerprints(bitstream);

        Run run = runItemUpdate(item,
                dublinCore(item, Collections.singletonList("embargoedAccess"), futureDate()));

        bitstream = context.reloadEntity(bitstream);

        assertUntouched("lease next to a dated embargo policy", idsBefore, policiesBefore, bitstream);
        assertExitCode("lease next to a dated embargo policy", 1, run);
    }

    /**
     * Verifies that a blank end date changes nothing: validation runs before anything is mutated.
     */
    @Test
    public void blankEmbargoEndLeavesPoliciesUntouched() throws Exception {
        Item item = assertEmbargoSyncIsANoOp("blank dc.date.embargoend",
                Collections.singletonList("openAccess"), "", 1);

        List<MetadataValue> storedEndDates = itemService.getMetadata(item, "dc", "date", "embargoend", Item.ANY);
        assertEquals("fixture precondition: the blank dc.date.embargoend has to be stored as an empty value."
                        + " If it were dropped the item would look like 'the operator removed the embargo' and"
                        + " this test would silently stop covering the blank value case.",
                1, storedEndDates.size());
        assertTrue("fixture precondition: the stored dc.date.embargoend has to be blank, not a real date",
                storedEndDates.get(0).getValue().trim().isEmpty());
    }

    /**
     * Verifies that parsing is strict: a lenient parser rolls 30 February over into 2 March and turns an
     * unreadable value into a real embargo date.
     */
    @Test
    public void invalidEmbargoEndLeavesPoliciesUntouched() throws Exception {
        // dynamic year, but 30 February and month 13 do not exist in any year
        int year = LocalDate.now().plusYears(1).getYear();
        List<String> invalidEndDates = Arrays.asList("abc", year + "-02-30", year + "-13-01");

        for (String invalidEndDate : invalidEndDates) {
            assertEmbargoSyncIsANoOp("unparseable dc.date.embargoend=" + invalidEndDate,
                    Collections.singletonList("openAccess"), invalidEndDate, 1);
        }
    }

    /**
     * Verifies that {@code embargoedAccess} without an end date is refused rather than half-applied.
     */
    @Test
    public void embargoedAccessWithoutEndDateLeavesPoliciesUntouched() throws Exception {
        assertEmbargoSyncIsANoOp("dc.rights.access=embargoedAccess without dc.date.embargoend",
                Collections.singletonList("embargoedAccess"), null, 1);
    }

    /**
     * Verifies that an item outside the archive is left alone; its bitstream policies belong to the
     * submission, not to itemupdate.
     */
    @Test
    public void notArchivedItemIsUntouched() throws Exception {
        Item item = createItem("Not archived thesis");
        Bitstream bitstream = createEmbargoedBitstream(item, "not-archived.pdf");

        // take the item back out of the archive; the handle stays, so the SAF update still resolves it
        context.turnOffAuthorisationSystem();
        item.setArchived(false);
        itemService.update(context, item);
        context.restoreAuthSystemState();

        item = context.reloadEntity(item);
        bitstream = context.reloadEntity(bitstream);

        assertFalse("fixture precondition: the item must not be archived", item.isArchived());
        assertFalse("fixture precondition: the embargoed file must not be publicly readable"
                + describe(bitstream), anonymousCanRead(bitstream));

        Set<Integer> idsBefore = policyIds(bitstream);
        List<String> policiesBefore = policyFingerprints(bitstream);

        Run run = runItemUpdate(item, dublinCore(item, Collections.singletonList("openAccess"), pastDate()));

        bitstream = context.reloadEntity(bitstream);

        assertExitCode("item outside the archive", 0, run);
        assertUntouched("item outside the archive", idsBefore, policiesBefore, bitstream);
        assertFalse("A file of an item outside the archive became publicly readable." + describe(bitstream),
                anonymousCanRead(bitstream));
    }

    /**
     * Verifies that a synchronisation which throws after re-dating a policy is not reported as success: the
     * per-item catch of {@code processArchive} prints the exception and commits what was written so far.
     */
    @Test
    public void embargoSyncThatDiesHalfWayIsNotReportedAsSuccess() throws Exception {
        Item item = createItem("Embargo sync dies half way");
        Bitstream bitstream = createEmbargoedBitstream(item, "half-way.pdf");

        assertFalse("fixture precondition: the embargoed file must not be publicly readable"
                + describe(bitstream), anonymousCanRead(bitstream));

        ItemUpdate itemUpdate = new ItemUpdate() {
            @Override
            protected void applyEmbargoToItemBitstreams(Context context, Item item, Date startDate)
                    throws SQLException, AuthorizeException {
                super.applyEmbargoToItemBitstreams(context, item, startDate);
                throw new IllegalStateException("simulated failure while deleting the duplicate policies");
            }
        };

        Run run = runItemUpdate(itemUpdate, item,
                dublinCore(item, Collections.singletonList("openAccess"), pastDate()));

        bitstream = context.reloadEntity(bitstream);

        assertTrue("fixture precondition: the simulated failure has to strike AFTER the surviving policy was"
                        + " re-dated, otherwise this test does not describe the dangerous case at all. The file"
                        + " is readable now, and that is the state context.complete() would commit."
                        + describe(bitstream),
                anonymousCanRead(bitstream));
        assertExitCode("embargo synchronisation that threw after re-dating a policy", 1, run);
    }

    /**
     * Runs one "hands off" scenario end to end and returns the reloaded item. The fixture is the state an
     * earlier run with a future end date leaves: one dated Anonymous READ policy currently blocking access.
     */
    private Item assertEmbargoSyncIsANoOp(String scenario, List<String> accessRights, String embargoEndDate,
                                          int expectedFailures)
            throws Exception {
        Item item = createItem("Safety scenario: " + scenario);
        Bitstream bitstream = createEmbargoedBitstream(item, "thesis.pdf");

        assertFalse("fixture precondition [" + scenario + "]: the embargoed file must not be publicly readable"
                + describe(bitstream), anonymousCanRead(bitstream));

        Set<Integer> idsBefore = policyIds(bitstream);
        List<String> policiesBefore = policyFingerprints(bitstream);

        Run run = runItemUpdate(item, dublinCore(item, accessRights, embargoEndDate));

        item = context.reloadEntity(item);
        bitstream = context.reloadEntity(bitstream);

        assertExitCode(scenario, expectedFailures, run);
        assertUntouched(scenario, idsBefore, policiesBefore, bitstream);
        assertFalse("[" + scenario + "] the embargoed file became publicly readable, which is a data leak"
                + describe(bitstream), anonymousCanRead(bitstream));

        return item;
    }

    /**
     * Compares policy identity first (ids), then policy content (fingerprints). The id comparison catches
     * delete-and-recreate, the fingerprint comparison catches in-place mutation of a surviving policy.
     */
    private void assertUntouched(String scenario, Set<Integer> idsBefore, List<String> policiesBefore,
                                 Bitstream bitstream) throws SQLException {
        assertEquals("[" + scenario + "] the set of resource policy ids changed: policies were deleted and/or"
                        + " re-created although nothing at all should have happened." + describe(bitstream),
                idsBefore, policyIds(bitstream));
        assertEquals("[" + scenario + "] a surviving resource policy was modified in place although nothing at"
                        + " all should have happened." + describe(bitstream),
                policiesBefore, policyFingerprints(bitstream));
    }

    /**
     * Runs itemupdate with both embargo fields as targets, the combination that triggers embargo
     * synchronisation. The {@link ItemUpdate} instance is kept because it carries the failure count.
     *
     * @return the console output of the run and the number of embargo problems it counted
     */
    private Run runItemUpdate(Item item, String dublinCoreContent) throws Exception {
        return runItemUpdate(new ItemUpdate(), item, dublinCoreContent);
    }

    /**
     * The same run driven by a caller supplied {@link ItemUpdate}, so that a test can make one step of the
     * synchronisation fail where a database error would.
     */
    private Run runItemUpdate(ItemUpdate itemUpdate, Item item, String dublinCoreContent) throws Exception {
        Path sourceRoot = Files.createDirectory(tempDir.resolve("saf-" + System.nanoTime()));
        // without this marker processArchive writes an undo archive next to the source directory
        Files.createFile(sourceRoot.resolve(ItemUpdate.SUPPRESS_UNDO_FILENAME));

        Path itemDir = Files.createDirectory(sourceRoot.resolve("item_000"));
        Files.writeString(itemDir.resolve("dublin_core.xml"), dublinCoreContent, StandardCharsets.UTF_8);

        DeleteMetadataAction deleteAction =
                (DeleteMetadataAction) itemUpdate.actionMgr.getUpdateAction(DeleteMetadataAction.class);
        deleteAction.addTargetFields(new String[] { "dc.rights.access", "dc.date.embargoend" });

        AddMetadataAction addAction =
                (AddMetadataAction) itemUpdate.actionMgr.getUpdateAction(AddMetadataAction.class);
        addAction.addTargetFields(new String[] { "dc.rights.access", "dc.date.embargoend" });

        // ItemUpdate reports to System.out only, so the operator console has to be captured to assert on it
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
     * Builds a {@code dublin_core.xml} carrying zero or more {@code dc.rights.access} values.
     *
     * @param embargoEndDate {@code null} omits {@code dc.date.embargoend} entirely, the empty string writes a
     *                       blank value (an empty XML element is dropped by the parser, so a single space is
     *                       written instead)
     */
    private String dublinCore(Item item, List<String> accessRights, String embargoEndDate) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<dublin_core schema=\"dc\">\n")
                .append("    <dcvalue element=\"identifier\" qualifier=\"uri\">")
                .append(ItemUpdate.HANDLE_PREFIX)
                .append(item.getHandle())
                .append("</dcvalue>\n");

        for (String accessRight : accessRights) {
            sb.append("    <dcvalue element=\"rights\" qualifier=\"access\">")
                    .append(accessRight)
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
     * A bitstream in the ORIGINAL bundle. It inherits the collection DEFAULT_BITSTREAM_READ and so carries one
     * undated Anonymous READ policy, the state a freshly imported SAF item is in.
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

    /**
     * The state an itemupdate run with a future end date leaves: a single dated legacy policy blocking access,
     * and nothing else between the public and the file.
     */
    private Bitstream createEmbargoedBitstream(Item item, String name) throws Exception {
        Bitstream bitstream = createOriginalBitstream(item, name);

        context.turnOffAuthorisationSystem();
        deletePolicies(bitstream, Constants.READ);
        ResourcePolicyBuilder.createResourcePolicy(context, null, anonymousGroup)
                .withAction(Constants.READ)
                .withDspaceObject(bitstream)
                .withName(LEGACY_EMBARGO_POLICY_NAME)
                .withStartDate(startOfDayUtc(LocalDate.now().plusYears(1)))
                .build();
        context.restoreAuthSystemState();

        return context.reloadEntity(bitstream);
    }

    /**
     * A bitstream whose {@code Anonymous}/{@code READ} access expires by itself, as the {@code lease} access
     * condition writes it: no start date and an end date six months out.
     */
    private Bitstream createLeasedBitstream(Item item, String name) throws Exception {
        Bitstream bitstream = createOriginalBitstream(item, name);

        context.turnOffAuthorisationSystem();
        deletePolicies(bitstream, Constants.READ);
        addLeasePolicy(bitstream);
        context.restoreAuthSystemState();

        return context.reloadEntity(bitstream);
    }

    private void addLeasePolicy(Bitstream bitstream) throws Exception {
        ResourcePolicyBuilder.createResourcePolicy(context, null, anonymousGroup)
                .withAction(Constants.READ)
                .withDspaceObject(bitstream)
                .withName(LEASE_POLICY_NAME)
                .withPolicyType(ResourcePolicy.TYPE_CUSTOM)
                .withEndDate(startOfDayUtc(LocalDate.now().plusMonths(6)))
                .build();
    }

    /**
     * Deletes policies one by one, because the bulk removal helpers issue an HQL delete and leave the
     * in-memory collection stale.
     *
     * @param actionId action to delete, or {@link #ALL_ACTIONS} for every policy regardless of action
     */
    private void deletePolicies(Bitstream bitstream, int actionId) throws Exception {
        List<ResourcePolicy> doomed = actionId == ALL_ACTIONS
                ? allPolicies(bitstream)
                : policiesForAction(bitstream, actionId);
        for (ResourcePolicy policy : doomed) {
            resourcePolicyService.delete(context, policy);
        }
    }

    private List<ResourcePolicy> allPolicies(Bitstream bitstream) throws SQLException {
        return new ArrayList<>(resourcePolicyService.find(context, bitstream));
    }

    private List<ResourcePolicy> policiesForAction(Bitstream bitstream, int actionId) throws SQLException {
        return new ArrayList<>(resourcePolicyService.find(context, bitstream, actionId));
    }

    private List<ResourcePolicy> anonymousReadPolicies(Bitstream bitstream) throws SQLException {
        return policiesForAction(bitstream, Constants.READ).stream()
                .filter(policy -> policy.getGroup() != null && anonymousGroup.equals(policy.getGroup()))
                .collect(Collectors.toList());
    }

    private Set<Integer> policyIds(Bitstream bitstream) throws SQLException {
        Set<Integer> ids = new TreeSet<>();
        for (ResourcePolicy policy : allPolicies(bitstream)) {
            ids.add(policy.getID());
        }
        return ids;
    }

    private List<String> policyFingerprints(Bitstream bitstream) throws SQLException {
        List<String> fingerprints = new ArrayList<>();
        for (ResourcePolicy policy : allPolicies(bitstream)) {
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
                .append("  bitstream=").append(bitstream.getID()).append(System.lineSeparator());
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

    private String pastDate() {
        return LocalDate.now().minusMonths(1).toString();
    }

    /**
     * A future end date, the branch that writes resource policies; the past-date branch returns early.
     */
    private String futureDate() {
        return LocalDate.now().plusYears(1).toString();
    }

    private Date startOfDayUtc(LocalDate day) {
        return Date.from(day.atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    /**
     * Renders a date at calendar day granularity: start dates come back from the database as
     * {@code java.sql.Date}, so comparing instants across the harness time zone would be flaky.
     */
    private String day(Date date) {
        if (date == null) {
            return "<none>";
        }
        if (date instanceof java.sql.Date) {
            return ((java.sql.Date) date).toLocalDate().toString();
        }
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().toString();
    }
}

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
 * Safety net for the VSB-TUO embargo synchronisation in {@link ItemUpdate}.
 *
 * <p>The bug being fixed is that an expired {@code dc.date.embargoend} strips the ORIGINAL bitstreams
 * of their last {@code Anonymous}/{@code READ} policy. The obvious repair - "when the embargo has
 * expired, just make the files public" - is far more dangerous than the bug itself, because it would
 * publish material that has to stay closed. This class pins down everything the repair must NOT do.</p>
 *
 * <p>Every "must not touch" assertion compares the full set of {@code policy_id} values plus a
 * fingerprint of every policy (action, group, rpType, rpName, start and end date). Comparing counts
 * would be useless: a policy deleted and immediately recreated keeps the count but loses its identity,
 * and a policy mutated in place keeps its id but changes its meaning.</p>
 */
public class EmbargoSafetyIT extends AbstractIntegrationTestWithDatabase {

    /**
     * rpName written by the shipped (buggy) implementation. The repair has to recognise and normalise
     * these legacy policies, so the fixtures use that name rather than a clean-room one.
     */
    private static final String LEGACY_EMBARGO_POLICY_NAME = "Standard Embargo";

    /**
     * The only supported way of re-opening files whose Anonymous READ policy is already gone.
     * ItemUpdate has to point the operator at it instead of inventing a public policy.
     */
    private static final String BULK_ACCESS_CONTROL_HINT = "bulk-access-control";

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
     * Specification row 9. A withdrawn item is hidden on purpose. Withdrawal converts every READ policy
     * into WITHDRAWN_READ, so an embargo sync that "restores" access would silently undo a takedown.
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

        // Which guard stopped the run has to be nailed down. ItemServiceImpl.withdraw() also clears
        // archived, so the !isArchived guard alone would satisfy every policy assertion below and this test
        // would keep passing after the withdrawal guard was deleted.
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

        // Row 9 says "any end date". The past-date run above only ever reaches the early return, so on its own
        // it proves nothing about withdrawal; the future-date branch is the one that creates policies.
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
     * Specification row 4. {@code restrictedAccess} means the files stay closed no matter what the embargo
     * end date says - a stale end date is not permission to publish.
     */
    @Test
    public void restrictedAccessWithStaleEmbargoEndIsUntouched() throws Exception {
        assertEmbargoSyncIsANoOp("dc.rights.access=restrictedAccess with a past embargo end",
                Collections.singletonList("restrictedAccess"), pastDate(), 0);
    }

    /**
     * Specification row 4. {@code metadataOnlyAccess} means the bitstreams are never disclosed.
     */
    @Test
    public void metadataOnlyAccessIsUntouched() throws Exception {
        assertEmbargoSyncIsANoOp("dc.rights.access=metadataOnlyAccess with a past embargo end",
                Collections.singletonList("metadataOnlyAccess"), pastDate(), 0);
    }

    /**
     * Specification row 4. An access right the tool does not understand is not an invitation to guess;
     * an unknown value means "hands off", never "open".
     */
    @Test
    public void unknownAccessRightValueIsUntouched() throws Exception {
        assertEmbargoSyncIsANoOp("dc.rights.access=someAccessRightWeDoNotKnow with a past embargo end",
                Collections.singletonList("someAccessRightWeDoNotKnow"), pastDate(), 0);
    }

    /**
     * Specification row 4. A single value outside the allowlist blocks the whole item, even when another
     * value of the same field says {@code openAccess}. Contradictory metadata is never resolved in favour
     * of disclosure.
     */
    @Test
    public void mixedAccessRightsWithOneDisallowedIsUntouched() throws Exception {
        assertEmbargoSyncIsANoOp("dc.rights.access=openAccess + restrictedAccess with a past embargo end",
                Arrays.asList("openAccess", "restrictedAccess"), pastDate(), 0);
    }

    /**
     * Specification row 11. When READ is granted to a named group only there is no Anonymous policy to
     * mutate. Creating one would hand the public a file that was deliberately limited to that group.
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

        // Row 11 says "any end date". A past date only reaches the early return; the future-date branch is the
        // one that creates policies, so it is where a group-restricted file can silently gain an Anonymous one.
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

        // Reporting is asserted last, after both legs have proved that no policy was invented: a missing log
        // line must never be the reason a reader stops reading before the policy damage is on screen.
        assertTrue("There is no Anonymous READ policy to re-date here, so ItemUpdate has to report the bitstream"
                        + " it could not synchronise and name '" + BULK_ACCESS_CONTROL_HINT + "' as the supported"
                        + " way to change access. Console output of the expired-embargo run was:"
                        + System.lineSeparator() + pastRun.console,
                pastRun.console.contains(BULK_ACCESS_CONTROL_HINT));
        assertTrue("ItemUpdate stayed silent about a bitstream it could not synchronise. Console output of the"
                        + " future-embargo run was:" + System.lineSeparator() + futureRun.console,
                futureRun.console.contains(BULK_ACCESS_CONTROL_HINT));

        // Spec row 11 requires a non-zero exit code: an unsynchronised bitstream that leaves the exit code at
        // 0 is invisible to the operator who started the batch.
        assertExitCode("bitstream without Anonymous READ, expired embargo", 1, pastRun);
        assertExitCode("bitstream without Anonymous READ, future embargo", 1, futureRun);
    }

    /**
     * Specification row 11. This is the exact state of the customer record damaged by the shipped code:
     * zero resource policies, HTTP 401 on every download. The repair must refuse to guess what those
     * policies used to be - it may only report the damage and name the tool that can undo it.
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
     * Specification row 7. A blank end date is a broken export, not an instruction. Validation has to run
     * before anything is mutated, so the existing policies survive untouched.
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
     * Specification row 8. Parsing has to be strict. {@code DCDate} silently rolls 30 February over into
     * 2 March, which would turn an unparseable value into a real - possibly future - embargo date.
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
     * Specification row 6. {@code embargoedAccess} without an end date is self-contradictory metadata.
     * The tool has to refuse it rather than pick one half of the contradiction.
     */
    @Test
    public void embargoedAccessWithoutEndDateLeavesPoliciesUntouched() throws Exception {
        assertEmbargoSyncIsANoOp("dc.rights.access=embargoedAccess without dc.date.embargoend",
                Collections.singletonList("embargoedAccess"), null, 1);
    }

    /**
     * Specification row 10. An item outside the archive (workspace or workflow) is not published yet; its
     * bitstream policies are the submission's business, not itemupdate's.
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
     * Runs one "ItemUpdate has to keep its hands off" scenario end to end and returns the reloaded item.
     *
     * <p>The fixture is the state the customer repository is in after an earlier itemupdate run with a
     * future end date: exactly one Anonymous READ policy, dated, currently blocking access. Any repair
     * that publishes, deletes or re-creates that policy is caught here.</p>
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
     * Equivalent of {@code dsrun ... ItemUpdate -s <saf> -d dc.rights.access -d dc.date.embargoend
     * -a dc.rights.access -a dc.date.embargoend}, i.e. an update whose target fields contain an embargo
     * field, which is what makes {@code processArchive} call {@code syncEmbargoPolicies}.
     *
     * <p>The {@link ItemUpdate} instance is kept, not thrown away: {@code embargoSyncFailures} is what
     * {@code main()} turns into a non-zero exit code, and an operator scripting {@code itemupdate} sees
     * nothing else. A refusal that leaves the exit code at 0 is a silent failure.</p>
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
     * Everything a finished {@code itemupdate} run is judged by: what it told the operator, and what it would
     * have exited with.
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
     * A run that refused to do something has to say so in its exit code, otherwise the operator's script
     * treats a skipped item as a synchronised one.
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
     * @param embargoEndDate {@code null} omits {@code dc.date.embargoend} entirely (the operator lifted the
     *                       embargo), the empty string writes a blank value (an empty XML element is dropped
     *                       by the parser, so a single space is written instead)
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
     * A bitstream in the ORIGINAL bundle. The collection grants DEFAULT_BITSTREAM_READ to Anonymous, so
     * BundleServiceImpl gives the new bitstream exactly one policy: Anonymous / READ / TYPE_INHERITED /
     * rpName=null / startDate=null - byte for byte the state of a freshly imported SAF item.
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
     * The state the customer repository is left in by an itemupdate run with a future end date: the
     * immediate Anonymous READ policy is gone and a single dated legacy "Standard Embargo" policy blocks
     * access. That policy is all that stands between the public and the file.
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
     * Deletes policies one by one through {@code ResourcePolicyService#delete}, the same call the production
     * code uses, so the Hibernate session stays consistent (the bulk removal helpers issue an HQL delete and
     * leave the in-memory collection stale).
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
     * Renders the current policies of the bitstream for failure messages. Whoever reads a red build has to
     * see which policy moved without re-running anything.
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
     * Answers the only question that matters: may a visitor who is not logged in download the file?
     *
     * <p>The authorisation state is a stack, not a flag: the builders and processArchive push and pop
     * around themselves, so the depth is not guaranteed to be zero here. It has to be drained, otherwise
     * {@code authorize()} short circuits on {@code ignoreAuthorization()} and every read looks allowed.</p>
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
     * A future end date is the dangerous half of every "hands off" rule: the past-date branch of the shipped
     * code returns early and therefore looks harmless, while the future-date branch is the one that actually
     * writes resource policies.
     */
    private String futureDate() {
        return LocalDate.now().plusYears(1).toString();
    }

    private Date startOfDayUtc(LocalDate day) {
        return Date.from(day.atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    /**
     * Compares dates at calendar day granularity. The harness forces TZ Europe/Dublin while resource policy
     * start dates come back from the database as {@code java.sql.Date}; comparing instants across that
     * boundary would be flaky, comparing days is not.
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

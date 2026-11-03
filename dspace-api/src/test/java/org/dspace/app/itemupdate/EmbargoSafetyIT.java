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

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.builder.GroupBuilder;
import org.dspace.builder.ResourcePolicyBuilder;
import org.dspace.content.Bitstream;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.eperson.Group;
import org.junit.Test;

/**
 * Pins down the cases in which embargo synchronisation in {@link ItemUpdate} has to leave a bitstream alone
 * rather than publish it. Every "must not touch" assertion compares policy ids and a fingerprint of every
 * policy, because counts hide both delete-and-recreate and in-place mutation.
 */
public class EmbargoSafetyIT extends AbstractEmbargoIT {

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

        Run pastRun = runItemUpdate(item, dublinCore(item, "openAccess", pastDate()));

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
                runItemUpdate(item, dublinCore(item, "embargoedAccess", futureDate()));

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

        Run pastRun = runItemUpdate(item, dublinCore(item, "openAccess", pastDate()));

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
                dublinCore(item, "embargoedAccess", futureDate()));

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

        Run run = runItemUpdate(item, dublinCore(item, "openAccess", pastDate()));

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
                dublinCore(item, "embargoedAccess", futureDate()));

        bitstream = context.reloadEntity(bitstream);

        assertUntouched("leased Anonymous READ policy, future embargo end", idsBefore, policiesBefore, bitstream);
        assertTrue("ItemUpdate has to name '" + BULK_ACCESS_CONTROL_HINT + "' as the supported way to change an"
                        + " access condition it does not manage. Console output was:" + System.lineSeparator()
                        + futureRun.console,
                futureRun.console.contains(BULK_ACCESS_CONTROL_HINT));
        assertExitCode("leased Anonymous READ policy, future embargo end", 1, futureRun);

        // An expired end date reaches the same mutation, only with a start date that has already passed.
        Run pastRun = runItemUpdate(item,
                dublinCore(item, "openAccess", pastDate()));

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
                dublinCore(item, "embargoedAccess", futureDate()));

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

        Run run = runItemUpdate(item, dublinCore(item, "openAccess", pastDate()));

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
                dublinCore(item, "openAccess", pastDate()));

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

        Run run = runItemUpdate(item, dublinCoreWithAccessRights(item, accessRights, embargoEndDate));

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

}

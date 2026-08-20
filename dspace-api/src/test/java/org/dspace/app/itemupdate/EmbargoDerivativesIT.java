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

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.dspace.authorize.ResourcePolicy;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Item;
import org.dspace.core.Constants;
import org.junit.Test;

/**
 * Embargo synchronisation of the bundles derived from an embargoed file. While an item is under embargo
 * neither its extracted full text (TEXT) nor its thumbnail (THUMBNAIL) may be readable, and both have to be
 * re-opened when the embargo ends. Bundles that are not derived from the file, LICENSE above all, stay out
 * of scope.
 */
public class EmbargoDerivativesIT extends AbstractEmbargoIT {

    private static final String OPEN_ACCESS = "openAccess";
    private static final String EMBARGOED_ACCESS = "embargoedAccess";
    private static final String RESTRICTED_ACCESS = "restrictedAccess";

    private static final String LICENSE_BUNDLE = "LICENSE";
    private static final String CC_LICENSE_BUNDLE = "CC-LICENSE";

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
            replaceAnonymousReadPolicies(file, startOfDayUtc(LocalDate.now().plusYears(1)),
                    EMBARGO_POLICY_NAME, ResourcePolicy.TYPE_CUSTOM);
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
     * Verifies that a file an operator routed into a bundle of its own follows the embargo. SAF packages can
     * do that with the {@code bundle:<name>} marker, and {@code DefaultEmbargoSetter} covers every bundle but
     * the licence and metadata ones, so leaving it public would disclose the embargoed work.
     */
    @Test
    public void customBundleFollowsTheEmbargo() throws Exception {
        LocalDate futureEnd = LocalDate.now().plusMonths(4);
        LocalDate pastEnd = LocalDate.now().minusMonths(4);

        Item item = createItem("Thesis with a supplement");
        Bitstream original = createBitstreamInBundle(item, "thesis.pdf", Constants.CONTENT_BUNDLE_NAME);
        Bitstream supplement = createBitstreamInBundle(item, "dataset.csv", "SUPPLEMENT");

        Run embargoRun = runItemUpdate(item, dublinCore(item, EMBARGOED_ACCESS, futureEnd.toString()));
        item = context.reloadEntity(item);
        original = context.reloadEntity(original);
        supplement = context.reloadEntity(supplement);

        assertExitCode("future embargo on an item with a custom bundle", 0, embargoRun);
        assertFalse("sanity check: the ORIGINAL bitstream has to be closed" + describe(original),
                anonymousCanRead(original));
        assertFalse("a file in a bundle of its own is part of the work and must be closed by the embargo"
                        + describe(supplement),
                anonymousCanRead(supplement));

        Run expiredRun = runItemUpdate(item, dublinCore(item, OPEN_ACCESS, pastEnd.toString()));
        item = context.reloadEntity(item);
        original = context.reloadEntity(original);
        supplement = context.reloadEntity(supplement);

        assertExitCode("expired embargo on an item with a custom bundle", 0, expiredRun);
        assertTrue("sanity check: the expired embargo has to publish the ORIGINAL bitstream"
                        + describe(original),
                anonymousCanRead(original));
        assertTrue("the custom bundle has to open together with the file it belongs to" + describe(supplement),
                anonymousCanRead(supplement));
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
        deletePolicies(text, Constants.READ);
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

    private ResourcePolicy onlyAnonymousReadPolicy(Bitstream bitstream, String what) throws SQLException {
        List<ResourcePolicy> policies = anonymousReadPolicies(bitstream);
        assertEquals("[" + label(bitstream) + "] exactly one Anonymous READ policy must remain after " + what
                        + ", a second one would defeat the embargo." + describe(bitstream),
                1, policies.size());
        return policies.get(0);
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

}

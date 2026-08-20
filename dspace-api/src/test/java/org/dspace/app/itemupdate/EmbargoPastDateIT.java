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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.dspace.authorize.ResourcePolicy;
import org.dspace.content.Bitstream;
import org.dspace.content.Item;
import org.dspace.core.Constants;
import org.dspace.eperson.Group;
import org.junit.Test;

/**
 * Covers embargo synchronisation in {@link ItemUpdate} when the same archive is re-imported with a
 * {@code dc.date.embargoend} that has already passed: the ORIGINAL bitstreams have to stay readable
 * for anonymous users instead of losing their last {@code READ} policy.
 */
public class EmbargoPastDateIT extends AbstractEmbargoIT {

    private final StringBuilder diagnostics = new StringBuilder();

    /**
     * Verifies that a future embargo followed by an expired one leaves the ORIGINAL bitstream publicly
     * readable.
     */
    @Test
    public void pastEmbargoEndMustKeepFilesPublic() throws Exception {
        String futureEmbargoEnd = LocalDate.now().plusYears(1).toString();
        String pastEmbargoEnd = LocalDate.now().minusMonths(1).toString();

        List<Group> defaultBitstreamReadGroups =
                authorizeService.getAuthorizedGroups(context, collection, Constants.DEFAULT_BITSTREAM_READ);
        assertTrue("fixture precondition: collection must grant DEFAULT_BITSTREAM_READ to Anonymous",
                defaultBitstreamReadGroups.contains(anonymousGroup));

        Item item = createItem("VSB-TUO thesis");
        Bitstream bitstream = createOriginalBitstream(item, "thesis.pdf");

        dump("STEP A - fresh SAF import, before any itemupdate", bitstream);
        assertFalse("fixture precondition: imported bitstream must carry an Anonymous READ policy",
                anonymousReadPolicies(bitstream).isEmpty());
        assertTrue("fixture precondition: imported bitstream must be publicly readable",
                anonymousCanRead(bitstream));

        // first run: embargo end date in the future
        runItemUpdateExpecting(item, dublinCore(item, "embargoedAccess", futureEmbargoEnd), 0);
        item = context.reloadEntity(item);
        bitstream = context.reloadEntity(bitstream);
        dump("STEP B - after itemupdate with FUTURE dc.date.embargoend=" + futureEmbargoEnd, bitstream);

        assertEquals("itemupdate did not store the future embargo end date",
                futureEmbargoEnd, singleMetadataValue(item, "date", "embargoend"));
        List<ResourcePolicy> embargoed = anonymousReadPolicies(bitstream);
        assertEquals("future embargo must leave exactly one Anonymous READ policy", 1, embargoed.size());
        assertNotNull("the surviving Anonymous READ policy must be dated", embargoed.get(0).getStartDate());
        assertFalse("while embargoed the file must not be publicly readable", anonymousCanRead(bitstream));

        // second run: embargo end date in the past, item declared openAccess
        runItemUpdateExpecting(item, dublinCore(item, "openAccess", pastEmbargoEnd), 0);
        item = context.reloadEntity(item);
        bitstream = context.reloadEntity(bitstream);
        dump("STEP C - after itemupdate with PAST dc.date.embargoend=" + pastEmbargoEnd
                + " and dc.rights.access=openAccess", bitstream);

        List<ResourcePolicy> afterExpiry = anonymousReadPolicies(bitstream);
        assertFalse("Expired embargo wiped every Anonymous READ policy from the ORIGINAL bitstream."
                        + " The file is now unreachable (HTTP 401) although dc.rights.access=openAccess."
                        + diagnostics,
                afterExpiry.isEmpty());
        assertTrue("Expired embargo left the ORIGINAL bitstream unreadable for anonymous users."
                        + diagnostics,
                anonymousCanRead(bitstream));
    }

    private void dump(String label, Bitstream bitstream) throws Exception {
        List<String> lines = new ArrayList<>();
        for (ResourcePolicy policy : resourcePolicyService.find(context, bitstream, Constants.READ)) {
            lines.add(String.format("      id=%s group=%s action=%s rpType=%s rpName=%s start=%s end=%s valid=%s",
                    policy.getID(),
                    policy.getGroup() == null ? "<none>" : policy.getGroup().getName(),
                    Constants.actionText[policy.getAction()],
                    policy.getRpType(),
                    policy.getRpName(),
                    policy.getStartDate(),
                    policy.getEndDate(),
                    resourcePolicyService.isDateValid(policy)));
        }
        if (lines.isEmpty()) {
            lines.add("      <NO READ POLICIES AT ALL>");
        }

        StringBuilder sb = new StringBuilder();
        sb.append(System.lineSeparator())
          .append("  === ").append(label).append(" ===").append(System.lineSeparator())
          .append("      bitstream=").append(bitstream.getID()).append(System.lineSeparator())
          .append("      anonymousCanRead=").append(anonymousCanRead(bitstream)).append(System.lineSeparator());
        for (String line : lines) {
            sb.append(line).append(System.lineSeparator());
        }
        diagnostics.append(sb);
        System.out.print(sb);
    }

}

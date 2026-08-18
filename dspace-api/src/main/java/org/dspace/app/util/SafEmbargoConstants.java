/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.util;

/**
 * Constants shared by the two SAF batch tools that write embargo resource policies:
 * {@code dspace import} ({@link org.dspace.app.itemimport.ItemImportServiceImpl}) creates the policy on a
 * freshly imported item, {@code dspace itemupdate} ({@link org.dspace.app.itemupdate.ItemUpdate}) later
 * re-dates and normalises it.
 *
 * <p>The two tools have to agree on the value, so it is declared once. When they drift apart the operator
 * sees two different names for the same thing in the policy list of a bitstream.</p>
 */
public final class SafEmbargoConstants {

    /**
     * Value written to {@code resourcepolicy.rpname} on every embargo policy created or adopted by the SAF
     * tools. It is the {@code name} of the {@code embargoed} access condition in access-conditions.xml, which
     * is what the submission UI and {@code dspace bulk-access-control} write, and it fits the 30 character
     * {@code rpname} column - the previous "Special Case Embargo - No access rights metadata" was 48
     * characters and aborted the whole import on PostgreSQL.
     */
    public static final String EMBARGO_POLICY_NAME = "embargo";

    private SafEmbargoConstants() {
    }
}

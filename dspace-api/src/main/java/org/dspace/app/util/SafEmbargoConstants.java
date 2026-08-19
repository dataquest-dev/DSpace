/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.util;

/**
 * Constants of the embargo resource policies written by the SAF batch tools, declared once so that
 * {@code dspace import} and {@code dspace itemupdate} cannot drift apart.
 */
public final class SafEmbargoConstants {

    /**
     * Value written to {@code resourcepolicy.rpname} on embargo policies. It is the name of the
     * {@code embargoed} access condition, as written by the submission UI and {@code bulk-access-control}, and
     * it fits the 30 character {@code rpname} column, which a longer name would overflow.
     */
    public static final String EMBARGO_POLICY_NAME = "embargo";

    private SafEmbargoConstants() {
    }
}

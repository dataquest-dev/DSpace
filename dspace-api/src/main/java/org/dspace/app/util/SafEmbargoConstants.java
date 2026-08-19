/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.dspace.core.Constants;

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

    /** Bundle holding the text extracted from a file; {@link Constants} has no name for it. */
    public static final String TEXT_BUNDLE_NAME = "TEXT";

    /** Bundle holding the thumbnail rendered from a file; {@link Constants} has no name for it. */
    public static final String THUMBNAIL_BUNDLE_NAME = "THUMBNAIL";

    /**
     * Bundles an embargo covers: the file itself and everything derived from it, because the thumbnail and the
     * extracted full text disclose the embargoed file. {@code filter.*.publicPermission} is ignored on purpose.
     */
    public static final List<String> EMBARGOED_BUNDLE_NAMES = Collections.unmodifiableList(Arrays.asList(
        Constants.CONTENT_BUNDLE_NAME, TEXT_BUNDLE_NAME, THUMBNAIL_BUNDLE_NAME));

    private SafEmbargoConstants() {
    }
}

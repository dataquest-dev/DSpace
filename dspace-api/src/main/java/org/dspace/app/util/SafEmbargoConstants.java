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
import org.dspace.license.service.CreativeCommonsService;

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
     * Bundles an embargo never covers, the same three {@code DefaultEmbargoSetter} leaves world readable.
     * Everything else is covered: the file, the thumbnail and the extracted full text disclose the embargoed
     * work, and so does a file an operator routed into a bundle of their own with the SAF
     * {@code bundle:<name>} marker. {@code filter.*.publicPermission} is ignored on purpose.
     */
    public static final List<String> NON_EMBARGOED_BUNDLE_NAMES = Collections.unmodifiableList(Arrays.asList(
        Constants.LICENSE_BUNDLE_NAME, CreativeCommonsService.CC_BUNDLE_NAME,
        Constants.METADATA_BUNDLE_NAME));

    /**
     * Whether an embargo covers the bundle.
     *
     * @param bundleName name of the bundle
     * @return false for the licence and metadata bundles, true for everything that holds the work itself
     */
    public static boolean isEmbargoed(String bundleName) {
        return !NON_EMBARGOED_BUNDLE_NAMES.contains(bundleName);
    }

    private SafEmbargoConstants() {
    }
}

/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.harvest.ore;

import org.dspace.content.crosswalk.CrosswalkException;

/**
 * Thrown when an ORE aggregated resource may not be fetched. It extends {@link CrosswalkException} so that
 * it travels through the existing crosswalk signatures without changing them.
 */
public class OreResourceRejectedException extends CrosswalkException {

    private final RejectionReason reason;
    private final String url;

    public OreResourceRejectedException(RejectionReason reason, String url, String detail) {
        super(buildMessage(reason, url, detail));
        this.reason = reason;
        this.url = url;
    }

    public OreResourceRejectedException(RejectionReason reason, String url, String detail, Throwable cause) {
        super(buildMessage(reason, url, detail), cause);
        this.reason = reason;
        this.url = url;
    }

    public RejectionReason getReason() {
        return reason;
    }

    /**
     * @return the URL that was refused; for a redirect chain this is the offending hop, not the original URL
     */
    public String getUrl() {
        return url;
    }

    private static String buildMessage(RejectionReason reason, String url, String detail) {
        StringBuilder message = new StringBuilder("ORE resource rejected [").append(reason).append("]: ").append(url);
        if (detail != null && !detail.isEmpty()) {
            message.append(" (").append(detail).append(')');
        }
        return message.toString();
    }
}

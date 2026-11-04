/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.harvest.ore;

/**
 * Why an ORE aggregated resource was refused by the egress policy.
 */
public enum RejectionReason {
    /** The URL scheme was neither http nor https. */
    SCHEME_NOT_ALLOWED,
    /** The authority carried credentials. */
    USERINFO_PRESENT,
    /** The authority could not be parsed into a usable host. */
    MALFORMED_AUTHORITY,
    /** The host is not the one hosting the OAI source and is not explicitly allowed. */
    HOST_NOT_ALLOWED,
    /** The host did not resolve to any address. */
    HOST_UNRESOLVABLE,
    /** The host resolved to an internal, private or otherwise reserved address. */
    ADDRESS_BLOCKED,
    /** The redirect chain exceeded the configured hop cap. */
    TOO_MANY_REDIRECTS,
    /** A redirect moved the request from https to http. */
    REDIRECT_DOWNGRADE,
    /** The response was larger than the configured byte cap. */
    RESPONSE_TOO_LARGE,
    /** The remote server did not return a usable response. */
    FETCH_FAILED
}

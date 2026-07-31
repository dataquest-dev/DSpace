/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.harvest.ore;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Decides whether a single ORE aggregated resource URL may be fetched. Checks are applied in a fixed order
 * and the first violation ends the check, so an unexpected input always fails closed.
 */
public class OreUrlValidator {

    private static final Logger log = LogManager.getLogger();

    private static final String HTTP = "http";
    private static final String HTTPS = "https";

    private final BlockedAddressPredicate blockedAddresses = new BlockedAddressPredicate();

    /**
     * Turn an href taken from an ORE document into a URI, refusing anything that is not usable.
     *
     * @param url the raw href
     * @return the parsed URI
     * @throws OreResourceRejectedException if the value is not an absolute, parseable URL
     */
    public static URI parse(String url) throws OreResourceRejectedException {
        try {
            URI uri = new URI(url);
            if (!uri.isAbsolute()) {
                throw new OreResourceRejectedException(RejectionReason.SCHEME_NOT_ALLOWED, url, "not absolute");
            }
            return uri;
        } catch (URISyntaxException e) {
            throw new OreResourceRejectedException(RejectionReason.MALFORMED_AUTHORITY, url, "unparseable URL", e);
        }
    }

    /**
     * Validate a URL against the egress policy.
     *
     * @param uri    the URL to fetch, or a single redirect hop of it
     * @param policy the rules to apply
     * @return the decision, carrying the resolved addresses when the URL is allowed
     */
    public Decision validate(URI uri, OreEgressPolicy policy) {
        if (uri == null) {
            return Decision.reject(null, RejectionReason.MALFORMED_AUTHORITY, "no URL");
        }

        // 1. only http(s); this is what keeps file:, jar:, ftp: and friends out
        String scheme = lower(uri.getScheme());
        if (!HTTP.equals(scheme) && !HTTPS.equals(scheme)) {
            return Decision.reject(uri, RejectionReason.SCHEME_NOT_ALLOWED, "scheme " + uri.getScheme());
        }

        // 2. no credentials in the authority
        String authority = uri.getRawAuthority();
        if (uri.getRawUserInfo() != null || (authority != null && authority.indexOf('@') >= 0)) {
            return Decision.reject(uri, RejectionReason.USERINFO_PRESENT, "credentials in authority");
        }

        // 3. a host we can actually resolve; upstream dereferences this without checking
        String host = uri.getHost();
        if (StringUtils.isBlank(host) || host.indexOf('%') >= 0) {
            return Decision.reject(uri, RejectionReason.MALFORMED_AUTHORITY, "authority " + authority);
        }

        // 4. host confinement, the only step the per-collection flag governs
        if (!policy.allowExternalUrls() && !isHostConfined(uri, scheme, host, policy)) {
            return Decision.reject(uri, RejectionReason.HOST_NOT_ALLOWED, "host " + host);
        }

        // 5. resolve now, so the addresses we vet are the ones we connect to
        List<InetAddress> addresses;
        try {
            addresses = Arrays.asList(InetAddress.getAllByName(host));
        } catch (UnknownHostException e) {
            return Decision.reject(uri, RejectionReason.HOST_UNRESOLVABLE, "host " + host);
        }
        if (addresses.isEmpty()) {
            return Decision.reject(uri, RejectionReason.HOST_UNRESOLVABLE, "host " + host);
        }

        // 6. every returned address must pass, whatever the flag says
        if (policy.blockInternalAddresses() && !isHostExempt(host, policy)) {
            for (InetAddress address : addresses) {
                String range = blockedAddresses.matchedRange(address);
                if (range != null && !isAddressExempt(address, policy)) {
                    return Decision.reject(uri, RejectionReason.ADDRESS_BLOCKED, range);
                }
            }
        }

        return Decision.allow(uri, addresses);
    }

    /**
     * The resource must share scheme and host with the collection's oai_source, or match a globally allowed
     * prefix. The port is deliberately not compared, so same-host-different-port setups keep working.
     */
    private boolean isHostConfined(URI uri, String scheme, String host, OreEgressPolicy policy) {
        URI anchor = policy.anchor();
        if (anchor != null && host.equalsIgnoreCase(anchor.getHost()) && scheme.equals(lower(anchor.getScheme()))) {
            return true;
        }
        for (String prefix : policy.allowedUrlPrefix()) {
            if (matchesAllowedPrefix(uri, host, prefix)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesAllowedPrefix(URI uri, String host, String prefix) {
        if (!prefix.contains("://")) {
            return host.equalsIgnoreCase(prefix); // bare hostname, the form documented in oai.cfg
        }
        URI allowed;
        try {
            allowed = new URI(prefix);
        } catch (URISyntaxException e) {
            log.warn("Ignoring unparseable {}allowedUrlPrefix entry: {}", OreEgressPolicy.CONFIG_PREFIX, prefix);
            return false;
        }
        if (allowed.getHost() == null || !host.equalsIgnoreCase(allowed.getHost())
            || !lower(uri.getScheme()).equals(lower(allowed.getScheme()))) {
            return false;
        }
        // the host was compared exactly above, so the remaining prefix test cannot be widened by a lookalike host
        String allowedPath = StringUtils.defaultString(allowed.getRawPath());
        return allowedPath.isEmpty() || "/".equals(allowedPath)
            || StringUtils.defaultString(uri.getRawPath()).startsWith(allowedPath);
    }

    private boolean isHostExempt(String host, OreEgressPolicy policy) {
        for (String entry : policy.allowedInternalHosts()) {
            if (entry.indexOf('/') < 0 && entry.equalsIgnoreCase(host)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAddressExempt(InetAddress address, OreEgressPolicy policy) {
        for (String entry : policy.allowedInternalHosts()) {
            if (entry.indexOf('/') >= 0 && cidrContains(entry, address)) {
                return true;
            }
        }
        return false;
    }

    private boolean cidrContains(String cidr, InetAddress address) {
        int slash = cidr.lastIndexOf('/');
        try {
            // the network part comes from configuration, not from the harvested document
            InetAddress network = InetAddress.getByName(cidr.substring(0, slash).trim());
            int bits = Integer.parseInt(cidr.substring(slash + 1).trim());
            return BlockedAddressPredicate.prefixMatches(address.getAddress(), network.getAddress(), bits);
        } catch (UnknownHostException | NumberFormatException e) {
            log.warn("Ignoring unparseable {}allowedInternalHosts entry: {}", OreEgressPolicy.CONFIG_PREFIX, cidr);
            return false;
        }
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    /**
     * The outcome of validating one URL.
     */
    public static final class Decision {

        private final boolean allowed;
        private final RejectionReason reason;
        private final URI uri;
        private final List<InetAddress> addresses;
        private final String detail;

        private Decision(boolean allowed, RejectionReason reason, URI uri, List<InetAddress> addresses,
                         String detail) {
            this.allowed = allowed;
            this.reason = reason;
            this.uri = uri;
            this.addresses = addresses;
            this.detail = detail;
        }

        static Decision allow(URI uri, List<InetAddress> addresses) {
            return new Decision(true, null, uri, Collections.unmodifiableList(addresses), null);
        }

        static Decision reject(URI uri, RejectionReason reason, String detail) {
            return new Decision(false, reason, uri, Collections.emptyList(), detail);
        }

        public boolean isAllowed() {
            return allowed;
        }

        public RejectionReason reason() {
            return reason;
        }

        public URI uri() {
            return uri;
        }

        /**
         * @return every address the host resolved to, empty when the URL was rejected
         */
        public List<InetAddress> addresses() {
            return addresses;
        }

        /**
         * @return a short explanation for the log; may name the blocked range, so keep it out of user-visible
         *         messages
         */
        public String detail() {
            return detail;
        }
    }
}

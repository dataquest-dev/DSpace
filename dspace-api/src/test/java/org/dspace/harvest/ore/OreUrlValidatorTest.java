/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.harvest.ore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.dspace.harvest.HarvestedCollection;
import org.dspace.services.ConfigurationService;
import org.junit.Test;

/**
 * Unit tests for {@link OreUrlValidator}. The policies are built by hand and every host is an IP literal, so
 * no test needs Spring or a DNS lookup.
 */
public class OreUrlValidatorTest {

    private static final String PREFIX = "oai.harvester.ore.file.";

    /** A public host, standing in for the repository the collection harvests from. */
    private static final String OAI_SOURCE = "http://8.8.8.8/oai/request";

    /** A different public host, standing in for the one an ORE record points at. */
    private static final String OTHER_HOST = "93.184.216.34";

    private final OreUrlValidator validator = new OreUrlValidator();

    private final Map<String, Object> config = new HashMap<>();

    @Test
    public void rejectsNonHttpSchemes() {
        OreEgressPolicy policy = policy(OAI_SOURCE);
        assertRejected(RejectionReason.SCHEME_NOT_ALLOWED, "file:///etc/passwd", policy);
        assertRejected(RejectionReason.SCHEME_NOT_ALLOWED, "jar:file:///opt/dspace/lib/api.jar!/dspace.cfg", policy);
        assertRejected(RejectionReason.SCHEME_NOT_ALLOWED, "ftp://8.8.8.8/private", policy);
        assertRejected(RejectionReason.SCHEME_NOT_ALLOWED, "netdoc:///etc/passwd", policy);
    }

    @Test
    public void rejectsAnAuthorityWithoutAHost() {
        // upstream dereferences this host without checking it and throws NPE
        assertRejected(RejectionReason.MALFORMED_AUTHORITY, "http:///etc/passwd", policy(OAI_SOURCE));
    }

    @Test
    public void rejectsCredentialsInTheAuthority() {
        OreEgressPolicy policy = policy(OAI_SOURCE);
        assertRejected(RejectionReason.USERINFO_PRESENT, "http://user:secret@8.8.8.8/x", policy);
        // the "real" host of this one is the metadata service, not the one that reads like a hostname
        assertRejected(RejectionReason.USERINFO_PRESENT, "http://8.8.8.8@169.254.169.254/latest/", policy);
    }

    @Test
    public void rejectsObfuscatedAddressForms() {
        // the decimal and IPv6-embedded forms resolve to an internal address and must be caught there, so
        // the policy is anchored on each host to let the address check be what rejects it
        assertRejected(RejectionReason.ADDRESS_BLOCKED, "http://2130706433/", anchoredOn("2130706433"));
        assertRejected(RejectionReason.ADDRESS_BLOCKED, "http://[::ffff:7f00:1]/", anchoredOn("[::ffff:7f00:1]"));
        // these never parse into a usable host in the first place
        assertRejected(RejectionReason.MALFORMED_AUTHORITY, "http://127.1/", anchoredOn("127.1"));
        assertRejected(RejectionReason.MALFORMED_AUTHORITY, "http://0x7f.0x0.0x0.0x1/", anchoredOn("0x7f.0x0.0x0.0x1"));
    }

    @Test
    public void blocksADifferentHost() {
        assertRejected(RejectionReason.HOST_NOT_ALLOWED, "http://" + OTHER_HOST + "/file.pdf", policy(OAI_SOURCE));
    }

    @Test
    public void allowsTheOaiSourceHost() {
        OreEgressPolicy policy = policy(OAI_SOURCE);
        assertAllowed("http://8.8.8.8/bitstream/123/1/file.pdf", policy);
        // the port is deliberately not part of the comparison
        assertAllowed("http://8.8.8.8:8080/bitstream/123/1/file.pdf", policy);
        // the scheme is compared lower-cased
        assertAllowed("HTTP://8.8.8.8/bitstream/123/1/file.pdf", policy);
    }

    @Test
    public void blocksASchemeMismatchWithTheOaiSource() {
        assertRejected(RejectionReason.HOST_NOT_ALLOWED, "http://8.8.8.8/file.pdf",
                       policy("https://8.8.8.8/oai/request"));
    }

    @Test
    public void allowsAHostListedInAllowedUrlPrefix() {
        config.put(PREFIX + "allowedUrlPrefix", new String[] {"http://" + OTHER_HOST + "/files"});
        OreEgressPolicy policy = policy(null);
        assertAllowed("http://" + OTHER_HOST + "/files/a.pdf", policy);
        assertRejected(RejectionReason.HOST_NOT_ALLOWED, "http://" + OTHER_HOST + "/elsewhere/a.pdf", policy);
        assertRejected(RejectionReason.HOST_NOT_ALLOWED, "https://" + OTHER_HOST + "/files/a.pdf", policy);

        // the bare hostname form documented in oai.cfg
        config.put(PREFIX + "allowedUrlPrefix", new String[] {OTHER_HOST});
        assertAllowed("https://" + OTHER_HOST + "/anything", policy(null));
    }

    @Test
    public void blocksInternalAddresses() {
        assertRejected(RejectionReason.ADDRESS_BLOCKED, "http://169.254.169.254/latest/meta-data/iam/",
                       anchoredOn("169.254.169.254"));
        assertRejected(RejectionReason.ADDRESS_BLOCKED, "http://100.100.100.200/latest/meta-data/",
                       anchoredOn("100.100.100.200"));
        assertRejected(RejectionReason.ADDRESS_BLOCKED, "http://127.0.0.1:8983/solr/statistics/select?q=*:*",
                       anchoredOn("127.0.0.1"));
        assertRejected(RejectionReason.ADDRESS_BLOCKED, "http://10.1.2.3/internal", anchoredOn("10.1.2.3"));
        assertRejected(RejectionReason.ADDRESS_BLOCKED, "http://[fd12:3456::1]/internal", anchoredOn("[fd12:3456::1]"));
    }

    @Test
    public void allowsAnInternalHostOnlyWhenItIsListed() {
        String solr = "http://127.0.0.1:8983/solr/statistics/select";
        assertRejected(RejectionReason.ADDRESS_BLOCKED, solr, anchoredOn("127.0.0.1"));

        config.put(PREFIX + "allowedInternalHosts", new String[] {"127.0.0.1"});
        assertAllowed(solr, anchoredOn("127.0.0.1"));

        // CIDR entries are matched against the resolved address instead of the host name
        config.put(PREFIX + "allowedInternalHosts", new String[] {"10.0.0.0/8"});
        assertAllowed("http://10.1.2.3/internal", anchoredOn("10.1.2.3"));
        assertRejected(RejectionReason.ADDRESS_BLOCKED, "http://192.168.1.1/internal", anchoredOn("192.168.1.1"));
    }

    @Test
    public void strictestPolicyRejectsAnExternalHost() {
        assertRejected(RejectionReason.HOST_NOT_ALLOWED, "http://" + OTHER_HOST + "/file.pdf",
                       OreEgressPolicy.strictest(configuration()));
        // a null harvest row must land on the same fail-closed policy
        assertRejected(RejectionReason.HOST_NOT_ALLOWED, "http://" + OTHER_HOST + "/file.pdf",
                       OreEgressPolicy.from(null, configuration()));
    }

    @Test
    public void rejectsAMissingUrl() {
        OreUrlValidator.Decision decision = validator.validate(null, policy(OAI_SOURCE));
        assertFalse(decision.isAllowed());
        assertEquals(RejectionReason.MALFORMED_AUTHORITY, decision.getReason());
    }

    @Test
    public void parseRejectsRelativeAndUnparseableUrls() throws Exception {
        assertEquals(URI.create("http://8.8.8.8/x"), OreUrlValidator.parse("http://8.8.8.8/x"));

        OreResourceRejectedException relative =
            assertThrows(OreResourceRejectedException.class, () -> OreUrlValidator.parse("/bitstream/1"));
        assertEquals(RejectionReason.SCHEME_NOT_ALLOWED, relative.getReason());

        OreResourceRejectedException unparseable =
            assertThrows(OreResourceRejectedException.class, () -> OreUrlValidator.parse("http://8.8.8.8/a b"));
        assertEquals(RejectionReason.MALFORMED_AUTHORITY, unparseable.getReason());
    }

    private void assertRejected(RejectionReason expected, String url, OreEgressPolicy policy) {
        OreUrlValidator.Decision decision = validator.validate(URI.create(url), policy);
        assertFalse(url + " must be rejected", decision.isAllowed());
        assertEquals(url, expected, decision.getReason());
    }

    private void assertAllowed(String url, OreEgressPolicy policy) {
        OreUrlValidator.Decision decision = validator.validate(URI.create(url), policy);
        assertTrue(url + " must be allowed, was rejected as " + decision.getReason(), decision.isAllowed());
        assertFalse("an allowed URL must carry the addresses it was validated against",
                    decision.getAddresses().isEmpty());
    }

    /** A policy confined to the given oai_source. */
    private OreEgressPolicy policy(String oaiSource) {
        HarvestedCollection harvestRow = mock(HarvestedCollection.class);
        when(harvestRow.getOaiSource()).thenReturn(oaiSource);
        return OreEgressPolicy.from(harvestRow, configuration());
    }

    /** A policy whose oai_source confines to exactly {@code host}, so the address checks are what a test exercises. */
    private OreEgressPolicy anchoredOn(String host) {
        return policy("http://" + host + "/oai/request");
    }

    /**
     * Answers with whatever default the caller passes in, so the tests run against the shipped defaults and
     * only the entries a test puts in {@link #config} differ.
     */
    private ConfigurationService configuration() {
        return mock(ConfigurationService.class, invocation -> {
            Object[] arguments = invocation.getArguments();
            if (arguments.length == 0) {
                return null;
            }
            Object value = config.get(arguments[0]);
            if (value != null) {
                return value;
            }
            return arguments.length > 1 ? arguments[1] : null;
        });
    }
}

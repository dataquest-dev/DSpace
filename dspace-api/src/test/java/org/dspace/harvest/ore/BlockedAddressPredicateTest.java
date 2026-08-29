/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.harvest.ore;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.junit.Test;

/**
 * Unit tests for {@link BlockedAddressPredicate}. Every address here is an IP literal, so no test performs a
 * DNS lookup.
 */
public class BlockedAddressPredicateTest {

    private final BlockedAddressPredicate predicate = new BlockedAddressPredicate();

    @Test
    public void blocksLoopbackAndWildcardAddresses() throws Exception {
        assertBlocked("127.0.0.1");
        assertBlocked("127.99.1.2");
        assertBlocked("0.0.0.0");
        assertBlocked("0.1.2.3");
        assertBlocked("::1");
        assertBlocked("::");
    }

    @Test
    public void blocksPrivateIPv4Addresses() throws Exception {
        assertBlocked("10.0.0.5");
        assertBlocked("172.16.0.9");
        assertBlocked("192.168.1.1");
    }

    @Test
    public void blocksCloudMetadataAddresses() throws Exception {
        // AWS, GCP and Azure all publish their instance metadata here
        assertBlocked(InetAddress.getByAddress(bytes(169, 254, 169, 254)));
        // Alibaba puts it in CGNAT space, which none of the java.net helpers report
        assertBlocked("100.100.100.200");
        assertBlocked("100.64.0.1");
    }

    @Test
    public void blocksReservedIPv4Ranges() throws Exception {
        assertBlocked("192.0.0.170");
        assertBlocked("192.0.2.1");
        assertBlocked("198.51.100.1");
        assertBlocked("203.0.113.1");
        assertBlocked("192.88.99.1");
        assertBlocked("198.18.0.1");
        assertBlocked("240.0.0.1");
        assertBlocked("255.255.255.255");
    }

    @Test
    public void blocksMulticastAddresses() throws Exception {
        assertBlocked("224.0.0.1");
        assertBlocked("ff02::1");
    }

    @Test
    public void blocksUniqueLocalIPv6Addresses() throws Exception {
        InetAddress uniqueLocal = InetAddress.getByName("fd12:3456::1");
        // the gap that matters most: the JDK does not consider fc00::/7 site-local
        assertFalse(uniqueLocal.isSiteLocalAddress());
        assertBlocked(uniqueLocal);
        assertBlocked("fc00::1");
    }

    @Test
    public void blocksReservedIPv6Ranges() throws Exception {
        assertBlocked("2001:db8::1");
        assertBlocked("100::1");
    }

    @Test
    public void blocksIPv4AddressesTunnelledInsideIPv6() throws Exception {
        assertBlocked("::ffff:127.0.0.1");
        assertBlocked("2002:7f00:0001::");
        assertBlocked("64:ff9b::7f00:1");
        assertBlocked("2001:0:4136:e378:8000:63bf:3fff:fdd2");
        // ::ffff:0:0:0/96 (RFC 6052 IPv4-translated) carries the IPv4 address two bytes further along
        assertBlocked("::ffff:0:7f00:1");
        assertBlocked("::ffff:0:a9fe:a9fe");
    }

    @Test
    public void allowsPublicAddresses() throws Exception {
        assertAllowed("8.8.8.8");
        assertAllowed("93.184.216.34");
        assertAllowed(InetAddress.getByAddress(bytes(1, 1, 1, 1)));
        assertAllowed("2606:4700:4700::1111");
        assertAllowed("2a00:1450:4001:80e::200e");
    }

    @Test
    public void namesTheRangeThatMatched() throws Exception {
        assertNull(predicate.matchedRange(InetAddress.getByName("8.8.8.8")));
        assertNotNull(predicate.matchedRange(InetAddress.getByName("100.100.100.200")));
        assertNotNull("an unresolved address must fail closed", predicate.matchedRange(null));
    }

    @Test
    public void comparesOnlyTheLeadingBitsOfAPrefix() throws Exception {
        byte[] network = InetAddress.getByName("10.0.0.0").getAddress();
        assertTrue(BlockedAddressPredicate.prefixMatches(address("10.255.3.4"), network, 8));
        assertFalse(BlockedAddressPredicate.prefixMatches(address("11.0.0.1"), network, 8));
        assertTrue(BlockedAddressPredicate.prefixMatches(address("10.0.0.1"), network, 0));
        // a prefix can never match across address families
        assertFalse(BlockedAddressPredicate.prefixMatches(address("::1"), network, 8));
        assertFalse(BlockedAddressPredicate.prefixMatches(address("10.255.3.4"), network, 99));
    }

    private void assertBlocked(String literal) throws UnknownHostException {
        assertBlocked(InetAddress.getByName(literal));
    }

    private void assertBlocked(InetAddress address) {
        assertTrue(address.getHostAddress() + " must be blocked", predicate.test(address));
    }

    private void assertAllowed(String literal) throws UnknownHostException {
        assertAllowed(InetAddress.getByName(literal));
    }

    private void assertAllowed(InetAddress address) {
        assertFalse(address.getHostAddress() + " must be allowed", predicate.test(address));
    }

    private byte[] address(String literal) throws UnknownHostException {
        return InetAddress.getByName(literal).getAddress();
    }

    private static byte[] bytes(int... values) {
        byte[] raw = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            raw[i] = (byte) values[i];
        }
        return raw;
    }
}

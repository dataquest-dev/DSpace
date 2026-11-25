/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.administer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.net.Inet6Address;
import java.net.InetAddress;

import org.junit.Test;

public class FileDownloaderTest {

    @Test
    public void testInternalAddressesAreBlocked() throws Exception {
        String[] internal = {"127.0.0.1", "0.0.0.0", "0.1.2.3", "10.0.0.1", "172.17.0.2", "192.168.1.1",
            "169.254.169.254", "100.64.0.1", "224.0.0.1", "::1", "::", "fe80::1", "fd00::1", "fc00::1",
            "::ffff:127.0.0.1"};
        for (String address : internal) {
            assertTrue(address, FileDownloader.isInternal(InetAddress.getByName(address)));
        }
    }

    @Test
    public void testIpv4MappedAddressFromDnsIsBlocked() throws Exception {
        // DNS answers stay Inet6Address, only literals are converted to Inet4Address
        byte[] mapped = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xff, (byte) 0xff, (byte) 172, 18, 0, 3};
        assertTrue(FileDownloader.isInternal(Inet6Address.getByAddress(null, mapped, -1)));
    }

    @Test
    public void testPublicAddressesAreAllowed() throws Exception {
        String[] external = {"8.8.8.8", "100.128.0.1", "195.113.20.1", "2001:4860:4860::8888"};
        for (String address : external) {
            assertFalse(address, FileDownloader.isInternal(InetAddress.getByName(address)));
        }
    }
}

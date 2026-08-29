/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.harvest.ore;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.function.Predicate;

/**
 * Decides whether an address is one the server must never be talked into fetching from: loopback, private,
 * link-local, CGNAT, reserved, documentation and cloud-metadata ranges.
 * <p>
 * Matching is done on the raw address bytes, never on the textual form, and IPv6 addresses that embed an
 * IPv4 address are unwrapped and re-checked recursively. {@code true} means BLOCKED.
 */
public class BlockedAddressPredicate implements Predicate<InetAddress> {

    /** Ranges the java.net helpers below do not cover. */
    private static final Range[] BLOCKED_RANGES = {
        // IPv4
        range(4, 8, "0.0.0.0/8 (this network)", 0),
        range(4, 10, "100.64.0.0/10 (CGNAT)", 100, 64),
        range(4, 24, "192.0.0.0/24 (IETF protocol assignments)", 192, 0, 0),
        range(4, 24, "192.0.2.0/24 (TEST-NET-1)", 192, 0, 2),
        range(4, 24, "198.51.100.0/24 (TEST-NET-2)", 198, 51, 100),
        range(4, 24, "203.0.113.0/24 (TEST-NET-3)", 203, 0, 113),
        range(4, 24, "192.88.99.0/24 (6to4 relay anycast)", 192, 88, 99),
        range(4, 15, "198.18.0.0/15 (benchmarking)", 198, 18),
        range(4, 4, "240.0.0.0/4 (reserved)", 240),
        // IPv6
        range(16, 7, "fc00::/7 (unique local)", 0xfc),
        range(16, 128, "::/128 (unspecified)", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
        range(16, 96, "::/96 (IPv4-compatible)", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
        range(16, 96, "::ffff:0:0:0/96 (IPv4-translated)", 0, 0, 0, 0, 0, 0, 0, 0, 0xff, 0xff, 0, 0),
        range(16, 96, "64:ff9b::/96 (NAT64)", 0x00, 0x64, 0xff, 0x9b, 0, 0, 0, 0, 0, 0, 0, 0),
        range(16, 64, "100::/64 (discard-only)", 0x01, 0x00, 0, 0, 0, 0, 0, 0),
        range(16, 32, "2001::/32 (Teredo)", 0x20, 0x01, 0x00, 0x00),
        range(16, 32, "2001:db8::/32 (documentation)", 0x20, 0x01, 0x0d, 0xb8),
        range(16, 16, "2002::/16 (6to4)", 0x20, 0x02)
    };

    @Override
    public boolean test(InetAddress address) {
        return matchedRange(address) != null;
    }

    /**
     * Describe why an address is blocked, for logging.
     *
     * @param address the address to check
     * @return a description such as "100.64.0.0/10 (CGNAT)", or null if the address is acceptable
     */
    public String matchedRange(InetAddress address) {
        if (address == null) {
            return "unresolved address";
        }

        String category = wellKnownCategory(address);
        if (category != null) {
            return category;
        }

        byte[] raw = address.getAddress();
        for (Range blocked : BLOCKED_RANGES) {
            if (blocked.contains(raw)) {
                return blocked.label;
            }
        }

        // an IPv6 address can carry an IPv4 address that has to be judged on its own merits
        InetAddress embedded = embeddedIPv4(raw);
        if (embedded != null) {
            String inner = matchedRange(embedded);
            if (inner != null) {
                return inner + " embedded in IPv6";
            }
        }

        return null;
    }

    /**
     * Compare the leading {@code bits} of two equally sized raw addresses.
     *
     * @param address the address bytes
     * @param network the network bytes
     * @param bits    the prefix length
     * @return true when the address falls inside the network
     */
    static boolean prefixMatches(byte[] address, byte[] network, int bits) {
        if (address.length != network.length || bits < 0 || bits > address.length * 8) {
            return false;
        }
        int wholeBytes = bits / 8;
        for (int i = 0; i < wholeBytes; i++) {
            if (address[i] != network[i]) {
                return false;
            }
        }
        int remainder = bits % 8;
        if (remainder == 0) {
            return true;
        }
        int mask = (0xff << (8 - remainder)) & 0xff;
        return (address[wholeBytes] & mask) == (network[wholeBytes] & mask);
    }

    private static String wellKnownCategory(InetAddress address) {
        if (address.isAnyLocalAddress()) {
            return "wildcard address";
        }
        if (address.isLoopbackAddress()) {
            return "loopback address";
        }
        if (address.isLinkLocalAddress()) {
            return "link-local address";
        }
        if (address.isSiteLocalAddress()) {
            return "site-local address";
        }
        if (address.isMulticastAddress()) {
            return "multicast address";
        }
        return null;
    }

    /**
     * Extract the IPv4 address tunnelled inside an IPv6 address, if there is one. Without this
     * 2002:7f00:0001:: would reach 127.0.0.1.
     */
    private static InetAddress embeddedIPv4(byte[] raw) {
        if (raw.length != 16) {
            return null;
        }

        byte[] extracted = null;
        if (isZero(raw, 10) && unsigned(raw[10]) == 0xff && unsigned(raw[11]) == 0xff) {
            extracted = slice(raw, 12);                          // ::ffff:0:0/96, IPv4-mapped
        } else if (isZero(raw, 8) && unsigned(raw[8]) == 0xff && unsigned(raw[9]) == 0xff
            && raw[10] == 0 && raw[11] == 0) {
            extracted = slice(raw, 12);                          // ::ffff:0:0:0/96, IPv4-translated
        } else if (isZero(raw, 12)) {
            extracted = slice(raw, 12);                          // ::/96, IPv4-compatible
        } else if (unsigned(raw[0]) == 0x20 && unsigned(raw[1]) == 0x02) {
            extracted = slice(raw, 2);                           // 2002::/16, 6to4
        } else if (unsigned(raw[0]) == 0x20 && unsigned(raw[1]) == 0x01 && raw[2] == 0 && raw[3] == 0) {
            extracted = invert(slice(raw, 12));                  // 2001::/32, Teredo stores it inverted
        } else if (raw[0] == 0 && unsigned(raw[1]) == 0x64 && unsigned(raw[2]) == 0xff && unsigned(raw[3]) == 0x9b) {
            extracted = slice(raw, 12);                          // 64:ff9b::/96, NAT64
        }

        if (extracted == null) {
            return null;
        }
        try {
            return InetAddress.getByAddress(extracted);
        } catch (UnknownHostException e) {
            return null; // unreachable: the array is always four bytes long
        }
    }

    private static boolean isZero(byte[] raw, int length) {
        for (int i = 0; i < length; i++) {
            if (raw[i] != 0) {
                return false;
            }
        }
        return true;
    }

    private static byte[] slice(byte[] raw, int from) {
        return Arrays.copyOfRange(raw, from, from + 4);
    }

    private static byte[] invert(byte[] raw) {
        for (int i = 0; i < raw.length; i++) {
            raw[i] = (byte) ~raw[i];
        }
        return raw;
    }

    private static int unsigned(byte value) {
        return value & 0xff;
    }

    private static Range range(int addressLength, int bits, String label, int... network) {
        byte[] bytes = new byte[addressLength];
        for (int i = 0; i < network.length; i++) {
            bytes[i] = (byte) network[i];
        }
        return new Range(bytes, bits, label);
    }

    /** A network prefix held as raw bytes; only the leading {@code bits} are ever compared. */
    private static final class Range {
        private final byte[] network;
        private final int bits;
        private final String label;

        private Range(byte[] network, int bits, String label) {
            this.network = network;
            this.bits = bits;
            this.label = label;
        }

        private boolean contains(byte[] address) {
            return prefixMatches(address, network, bits);
        }
    }
}

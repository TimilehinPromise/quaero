package org.timmy.knowcommute.security;

import java.net.InetAddress;
import org.springframework.stereotype.Component;

/**
 * Decides whether an address is one the backend must never fetch from. Shared by the URL
 * validator, which checks before resolving, and the article fetcher, which checks the address
 * it actually connected to.
 */
@Component
public class PrivateAddressPolicy {

    public boolean isPrivate(InetAddress address) {
        return address.isLoopbackAddress()
                || address.isAnyLocalAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()
                || isUniqueLocalIpv6(address)
                || isCarrierGradeNat(address);
    }

    private boolean isUniqueLocalIpv6(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
    }

    private boolean isCarrierGradeNat(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 4 && (bytes[0] & 0xFF) == 100 && (bytes[1] & 0xC0) == 64;
    }
}

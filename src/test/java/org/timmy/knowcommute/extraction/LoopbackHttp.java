package org.timmy.knowcommute.extraction;

import java.net.InetAddress;
import org.timmy.knowcommute.bookmark.HostResolver;
import org.timmy.knowcommute.security.PrivateAddressPolicy;

/** Lets the SSRF controls stay real while the article under test is served from loopback. */
final class LoopbackHttp {

    static final String INTERNAL_HOST = "internal.example";

    /** Real policy in every respect except loopback, so the test server is reachable. */
    static final PrivateAddressPolicy ALLOWING_LOOPBACK = new PrivateAddressPolicy() {
        @Override
        public boolean isPrivate(InetAddress address) {
            return !address.isLoopbackAddress() && super.isPrivate(address);
        }
    };

    /** Resolves the test server to loopback and one named host into the private range. */
    static final HostResolver RESOLVER = host -> INTERNAL_HOST.equals(host)
            ? new InetAddress[] {InetAddress.getByName("10.0.0.1")}
            : new InetAddress[] {InetAddress.getByName(host)};

    private LoopbackHttp() {
    }
}

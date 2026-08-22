package org.timmy.knowcommute.bookmark;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.springframework.stereotype.Component;

/**
 * Seam over DNS so {@link UrlValidator}'s address rules can be exercised without a network,
 * including multi-record hosts that real DNS cannot be made to produce on demand.
 */
public interface HostResolver {

    InetAddress[] resolve(String host) throws UnknownHostException;

    @Component
    class SystemHostResolver implements HostResolver {

        @Override
        public InetAddress[] resolve(String host) throws UnknownHostException {
            return InetAddress.getAllByName(host);
        }
    }
}

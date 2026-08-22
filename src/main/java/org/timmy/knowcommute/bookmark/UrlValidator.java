package org.timmy.knowcommute.bookmark;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.timmy.knowcommute.security.PrivateAddressPolicy;

/**
 * Rejects URLs the backend must not fetch. This is an SSRF control, not a formatting check:
 * the pipeline issues server-side requests to whatever a client submits, so a URL resolving
 * into the VPS or the hosting provider's metadata service must never reach the fetcher.
 *
 * <p>Callers must re-validate every redirect target — passing this check once says nothing
 * about where a redirect chain ends up.
 */
@Component
@RequiredArgsConstructor
public class UrlValidator {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");
    private static final int MAX_URL_LENGTH = 2048;

    private final HostResolver hostResolver;
    private final PrivateAddressPolicy addressPolicy;

    public URI validate(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            throw new InvalidUrlException("URL is required");
        }
        if (candidate.length() > MAX_URL_LENGTH) {
            throw new InvalidUrlException("URL exceeds " + MAX_URL_LENGTH + " characters");
        }

        URI uri = parse(candidate.trim());
        requireAllowedScheme(uri);
        requireNoCredentials(uri);
        String host = requireHost(uri);
        requirePubliclyRoutable(host);
        return uri;
    }

    private URI parse(String candidate) {
        try {
            URI uri = new URI(candidate);
            if (!uri.isAbsolute()) {
                throw new InvalidUrlException("URL must be absolute");
            }
            return uri;
        } catch (java.net.URISyntaxException e) {
            throw new InvalidUrlException("URL is malformed");
        }
    }

    private void requireAllowedScheme(URI uri) {
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if (!ALLOWED_SCHEMES.contains(scheme)) {
            throw new InvalidUrlException("Only http and https URLs are supported");
        }
    }

    private void requireNoCredentials(URI uri) {
        if (uri.getUserInfo() != null) {
            throw new InvalidUrlException("URL must not contain credentials");
        }
    }

    private String requireHost(URI uri) {
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new InvalidUrlException("URL must contain a host");
        }
        return host;
    }

    /**
     * Every address the host resolves to must be public. A name can legitimately return
     * several records, and checking only the first lets an attacker pair one public address
     * with one internal one.
     */
    private void requirePubliclyRoutable(String host) {
        InetAddress[] addresses;
        try {
            addresses = hostResolver.resolve(host);
        } catch (UnknownHostException e) {
            throw new InvalidUrlException("URL host could not be resolved");
        }

        for (InetAddress address : addresses) {
            if (addressPolicy.isPrivate(address)) {
                throw new InvalidUrlException("URL resolves to a non-public address");
            }
        }
    }

}

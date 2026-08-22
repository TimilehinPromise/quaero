package org.timmy.knowcommute.bookmark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UrlValidatorTest {

    private static final String PUBLIC_IP = "93.184.216.34";

    private final StubHostResolver resolver = new StubHostResolver();
    private final UrlValidator validator = new UrlValidator(resolver);

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.com/article",
            "http://example.com/article?utm_source=x",
            "https://sub.example.com:8443/a/b/c"
    })
    void acceptsPublicHttpUrls(String url) {
        resolver.resolvesTo(PUBLIC_IP);

        assertThat(validator.validate(url)).hasToString(url);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "169.254.169.254",
            "127.0.0.1",
            "10.0.0.5",
            "192.168.1.1",
            "172.16.0.1",
            "0.0.0.0",
            "100.64.0.1",
            "::1",
            "fd00::1"
    })
    void rejectsHostsResolvingToNonPublicAddresses(String address) {
        resolver.resolvesTo(address);

        assertThatExceptionOfType(InvalidUrlException.class)
                .isThrownBy(() -> validator.validate("https://article.example/post"));
    }

    @Test
    void rejectsHostWhereAnyRecordIsPrivate() {
        resolver.resolvesTo(PUBLIC_IP, "169.254.169.254");

        assertThatExceptionOfType(InvalidUrlException.class)
                .isThrownBy(() -> validator.validate("https://article.example/post"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "file:///etc/passwd",
            "gopher://example.com/",
            "ftp://example.com/x",
            "jar:http://example.com/a.jar!/"
    })
    void rejectsNonHttpSchemes(String url) {
        resolver.resolvesTo(PUBLIC_IP);

        assertThatExceptionOfType(InvalidUrlException.class)
                .isThrownBy(() -> validator.validate(url));
    }

    @Test
    void rejectsEmbeddedCredentials() {
        resolver.resolvesTo(PUBLIC_IP);

        assertThatExceptionOfType(InvalidUrlException.class)
                .isThrownBy(() -> validator.validate("http://admin:secret@example.com/"));
    }

    @Test
    void rejectsRelativeUrls() {
        assertThatExceptionOfType(InvalidUrlException.class)
                .isThrownBy(() -> validator.validate("/just/a/path"));
    }

    @Test
    void rejectsBlankInput() {
        assertThatExceptionOfType(InvalidUrlException.class)
                .isThrownBy(() -> validator.validate("   "));
    }

    @Test
    void rejectsOverlyLongUrls() {
        assertThatExceptionOfType(InvalidUrlException.class)
                .isThrownBy(() -> validator.validate("https://example.com/" + "a".repeat(2100)));
    }

    @Test
    void rejectsHostsThatDoNotResolve() {
        resolver.failsToResolve();

        assertThatExceptionOfType(InvalidUrlException.class)
                .isThrownBy(() -> validator.validate("https://no-such-host.example/article"));
    }

    private static final class StubHostResolver implements HostResolver {

        private String[] addresses = {PUBLIC_IP};
        private boolean fails;

        void resolvesTo(String... addresses) {
            this.addresses = addresses;
            this.fails = false;
        }

        void failsToResolve() {
            this.fails = true;
        }

        @Override
        public InetAddress[] resolve(String host) throws UnknownHostException {
            if (fails) {
                throw new UnknownHostException(host);
            }
            InetAddress[] resolved = new InetAddress[addresses.length];
            for (int i = 0; i < addresses.length; i++) {
                resolved[i] = InetAddress.getByName(addresses[i]);
            }
            return resolved;
        }
    }
}

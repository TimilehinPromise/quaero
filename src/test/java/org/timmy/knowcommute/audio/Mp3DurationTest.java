package org.timmy.knowcommute.audio;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class Mp3DurationTest {

    private byte[] fixture(String name) {
        try (var in = new ClassPathResource("fixtures/" + name).getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void readsAnMpeg2Stream() {
        // 24 kHz is what Kokoro emits, and 24 kHz is MPEG-2, not MPEG-1.
        assertThat(Mp3Duration.seconds(fixture("six-second-tone.mp3"))).isEqualTo(6);
    }

    @Test
    void readsAnMpeg1Stream() {
        assertThat(Mp3Duration.seconds(fixture("nine-second-tone-44k.mp3"))).isEqualTo(9);
    }

    @Test
    void returnsZeroForBytesThatAreNotMp3() {
        assertThat(Mp3Duration.seconds("this is not audio".getBytes())).isZero();
    }

    @Test
    void returnsZeroForEmptyInput() {
        assertThat(Mp3Duration.seconds(new byte[0])).isZero();
    }
}

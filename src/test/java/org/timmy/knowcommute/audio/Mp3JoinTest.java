package org.timmy.knowcommute.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class Mp3JoinTest {

    private byte[] fixture(String name) {
        try (var in = new ClassPathResource("fixtures/" + name).getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private byte[] sixSecondTone() {
        return fixture("six-second-tone.mp3");
    }

    private static boolean contains(byte[] data, String tag) {
        byte[] needle = tag.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        for (int i = 0; i + needle.length <= data.length; i++) {
            if (Arrays.equals(data, i, i + needle.length, needle, 0, needle.length)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void returnsASinglePartUntouched() {
        byte[] only = sixSecondTone();

        assertThat(Mp3Join.join(List.of(only))).isSameAs(only);
    }

    @Test
    void addsTheDurationsOfEveryPart() {
        byte[] joined = Mp3Join.join(List.of(sixSecondTone(), sixSecondTone(), sixSecondTone()));

        assertThat(Mp3Duration.seconds(joined)).isEqualTo(18);
    }

    @Test
    void dropsTheId3HeaderThatWouldOtherwiseSitMidStream() {
        assertThat(contains(sixSecondTone(), "ID3")).isTrue();

        assertThat(contains(Mp3Join.join(List.of(sixSecondTone(), sixSecondTone())), "ID3")).isFalse();
    }

    @Test
    void dropsTheXingFrameWhoseCountsDescribeOnePartOnly() {
        assertThat(contains(sixSecondTone(), "Info")).isTrue();

        assertThat(contains(Mp3Join.join(List.of(sixSecondTone(), sixSecondTone())), "Info")).isFalse();
    }

    @Test
    void refusesToJoinPartsThatDisagreeOnSampleRate() {
        List<byte[]> mismatched = List.of(sixSecondTone(), fixture("nine-second-tone-44k.mp3"));

        assertThatExceptionOfType(AudioGenerationException.class)
                .isThrownBy(() -> Mp3Join.join(mismatched))
                .withMessageContaining("changed audio format");
    }

    @Test
    void rejectsAPartCarryingNoAudioFrames() {
        List<byte[]> parts = List.of(sixSecondTone(), "not audio".getBytes());

        assertThatExceptionOfType(AudioGenerationException.class)
                .isThrownBy(() -> Mp3Join.join(parts));
    }
}

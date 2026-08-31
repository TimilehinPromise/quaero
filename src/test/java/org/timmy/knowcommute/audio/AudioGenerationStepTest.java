package org.timmy.knowcommute.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.audio.tts.TextToSpeechResponse;
import org.springframework.core.io.ClassPathResource;
import org.timmy.knowcommute.bookmark.Bookmark;
import org.timmy.knowcommute.bookmark.BookmarkStatus;
import org.timmy.knowcommute.bookmark.SummaryLength;
import org.timmy.knowcommute.user.DeviceUser;

class AudioGenerationStepTest {

    private final List<String> narratedParts = new ArrayList<>();
    private final AtomicReference<byte[]> audio = new AtomicReference<>(realMp3());
    private final Map<String, byte[]> stored = new HashMap<>();
    private final List<String> deleted = new ArrayList<>();

    private static byte[] realMp3() {
        try (var in = new ClassPathResource("fixtures/six-second-tone.mp3").getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private final TextToSpeechModel speechModel = new TextToSpeechModel() {
        @Override
        public byte[] call(String text) {
            narratedParts.add(text);
            return audio.get();
        }

        @Override
        public TextToSpeechResponse call(TextToSpeechPrompt prompt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public reactor.core.publisher.Flux<TextToSpeechResponse> stream(TextToSpeechPrompt prompt) {
            throw new UnsupportedOperationException();
        }
    };

    private final AudioStorage storage = new AudioStorage() {
        @Override
        public String store(String key, byte[] mp3) {
            stored.put(key, mp3);
            return key;
        }

        @Override
        public URI presignedUrl(String key) {
            return URI.create("https://example.com/" + key);
        }

        @Override
        public void delete(String key) {
            deleted.add(key);
        }

        @Override
        public List<String> keysOlderThan(Instant cutoff) {
            return List.copyOf(stored.keySet());
        }
    };

    private static final int CHUNK_CHARS = 1200;

    private final TtsProperties ttsProperties = new TtsProperties(
            null, null, null, null, null, null, java.time.Duration.ofMinutes(1), 1,
            CHUNK_CHARS, null, null);

    private final AudioGenerationStep step =
            new AudioGenerationStep(speechModel, storage, ttsProperties);

    private Bookmark summarised() {
        Bookmark bookmark = Bookmark.pending(
                DeviceUser.withDeviceId("device"), "https://example.com/a", SummaryLength.MEDIUM);
        bookmark.applyExtraction("A Title", null, "example.com", "body", 400, "hash");
        bookmark.applySummary("This is the narration script.");
        return bookmark;
    }

    @Test
    void narratesTheSummaryAndStoresTheAudio() {
        Bookmark bookmark = summarised();

        step.execute(bookmark);

        assertThat(narratedParts).containsExactly("This is the narration script.");
        assertThat(bookmark.getStatus()).isEqualTo(BookmarkStatus.READY);
        assertThat(stored).containsKey(bookmark.getAudioStorageKey());
    }

    @Test
    void recordsTheRealDurationRatherThanAnEstimate() {
        Bookmark bookmark = summarised();

        step.execute(bookmark);

        assertThat(bookmark.getAudioDurationSec()).isEqualTo(6);
    }

    @Test
    void storesAKeyRatherThanAUrl() {
        Bookmark bookmark = summarised();

        step.execute(bookmark);

        assertThat(bookmark.getAudioStorageKey())
                .isEqualTo("audio/" + bookmark.getId() + ".mp3")
                .doesNotStartWith("http");
    }

    @Test
    void reprocessingOverwritesRatherThanOrphaningAnObject() {
        Bookmark bookmark = summarised();

        step.execute(bookmark);
        String firstKey = bookmark.getAudioStorageKey();
        step.execute(bookmark);

        assertThat(bookmark.getAudioStorageKey()).isEqualTo(firstKey);
        assertThat(stored).hasSize(1);
    }

    @Test
    void refusesToNarrateABookmarkWithNoSummary() {
        Bookmark bookmark = Bookmark.pending(
                DeviceUser.withDeviceId("device"), "https://example.com/a", SummaryLength.MEDIUM);

        assertThatExceptionOfType(AudioGenerationException.class)
                .isThrownBy(() -> step.execute(bookmark));
    }

    @Test
    void failsWhenTheModelReturnsNoAudio() {
        audio.set(new byte[0]);

        assertThatExceptionOfType(AudioGenerationException.class)
                .isThrownBy(() -> step.execute(summarised()));
        assertThat(stored).isEmpty();
    }

    private Bookmark summarised(String narration) {
        Bookmark bookmark = Bookmark.pending(
                DeviceUser.withDeviceId("device"), "https://example.com/a", SummaryLength.LONG);
        bookmark.applyExtraction("A Title", null, "example.com", "body", 400, "hash");
        bookmark.applySummary(narration);
        return bookmark;
    }

    private static String longNarration() {
        return "Deterministic execution cannot produce an arithmetic error. ".repeat(80).trim();
    }

    @Test
    void narratesALongSummaryInSeveralRequestsRatherThanOne() {
        step.execute(summarised(longNarration()));

        assertThat(narratedParts).hasSizeGreaterThan(1)
                .allSatisfy(part -> assertThat(part).hasSizeLessThanOrEqualTo(CHUNK_CHARS));
    }

    @Test
    void narratesEveryWordOfALongSummaryExactlyOnce() {
        String narration = longNarration();

        step.execute(summarised(narration));

        assertThat(String.join(" ", narratedParts)).isEqualTo(narration);
    }

    @Test
    void joinsThePartsIntoOnePlayableFileWhoseDurationIsTheirSum() {
        Bookmark bookmark = summarised(longNarration());

        step.execute(bookmark);

        int parts = narratedParts.size();
        assertThat(bookmark.getAudioDurationSec()).isEqualTo(6 * parts);
        assertThat(stored.get(bookmark.getAudioStorageKey())).hasSizeGreaterThan(parts);
    }

    @Test
    void leavesNoMetadataTagInsideTheJoinedStream() {
        Bookmark bookmark = summarised(longNarration());

        step.execute(bookmark);

        byte[] joined = stored.get(bookmark.getAudioStorageKey());
        assertThat(occurrences(joined, "ID3")).isZero();
        assertThat(occurrences(joined, "Info")).isZero();
        assertThat(occurrences(joined, "Xing")).isZero();
    }

    private static int occurrences(byte[] data, String tag) {
        byte[] needle = tag.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        int found = 0;
        for (int i = 0; i + needle.length <= data.length; i++) {
            if (java.util.Arrays.equals(data, i, i + needle.length, needle, 0, needle.length)) {
                found++;
            }
        }
        return found;
    }

    @Test
    void handlesTheGeneratingAudioStatus() {
        assertThat(step.handles()).isEqualTo(BookmarkStatus.GENERATING_AUDIO);
    }
}

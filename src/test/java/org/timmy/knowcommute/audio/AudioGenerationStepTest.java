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

    private final AtomicReference<String> narratedText = new AtomicReference<>();
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
            narratedText.set(text);
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

    private final AudioGenerationStep step = new AudioGenerationStep(speechModel, storage);

    private Bookmark summarised() {
        Bookmark bookmark = Bookmark.pending(
                DeviceUser.withDeviceId("device"), "https://example.com/a", SummaryLength.MEDIUM);
        bookmark.applyExtraction("A Title", "example.com", "body", 400, "hash");
        bookmark.applySummary("This is the narration script.");
        return bookmark;
    }

    @Test
    void narratesTheSummaryAndStoresTheAudio() {
        Bookmark bookmark = summarised();

        step.execute(bookmark);

        assertThat(narratedText.get()).isEqualTo("This is the narration script.");
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

    @Test
    void handlesTheGeneratingAudioStatus() {
        assertThat(step.handles()).isEqualTo(BookmarkStatus.GENERATING_AUDIO);
    }
}

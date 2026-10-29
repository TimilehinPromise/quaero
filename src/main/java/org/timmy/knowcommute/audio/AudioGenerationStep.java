package org.timmy.knowcommute.audio;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.timmy.knowcommute.bookmark.Bookmark;
import org.timmy.knowcommute.bookmark.BookmarkStatus;
import org.timmy.knowcommute.pipeline.PipelineStep;

@Slf4j
@Component
public class AudioGenerationStep implements PipelineStep {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final CaptionedSpeechModel speechModel;
    private final AudioStorage storage;
    private final Semaphore synthesisSlots;
    private final int chunkChars;

    public AudioGenerationStep(CaptionedSpeechModel speechModel, AudioStorage storage,
                               TtsProperties properties) {
        this.speechModel = speechModel;
        this.storage = storage;
        this.synthesisSlots = new Semaphore(Math.max(properties.maxConcurrent(), 1), true);
        this.chunkChars = Math.max(properties.chunkChars(), 1);
    }

    @Override
    public BookmarkStatus handles() {
        return BookmarkStatus.GENERATING_AUDIO;
    }

    @Override
    public void execute(Bookmark bookmark) {
        String summary = bookmark.getSummaryText();
        if (summary == null || summary.isBlank()) {
            throw new AudioGenerationException("Bookmark has no summary to narrate");
        }

        GeneratedAudio generated = synthesise(summary, NarrationChunker.chunkWithOffsets(summary, chunkChars));
        if (generated.mp3().length == 0) {
            throw new AudioGenerationException("Speech model returned no audio");
        }

        String key = storage.store(storageKey(bookmark), generated.mp3());
        storage.storeTimestamps(key, serialise(generated.timings()));
        bookmark.applyAudio(key, Mp3Duration.seconds(generated.mp3()));
    }

    /**
     * Synthesis is the heaviest thing this application does — a self-hosted model holds well
     * over a gigabyte per request and runs slower than realtime. The pipeline's own concurrency
     * governs cheap steps like fetching; letting it govern this one too is what takes the
     * speech server down.
     *
     * <p>One slot covers the whole narration rather than each part. Per-part acquisition would
     * let every worker interleave its requests, putting as many bookmarks on the speech host at
     * once as there are workers, which is the load this exists to bound.
     */
    private GeneratedAudio synthesise(String summary, List<NarrationChunk> chunks) {
        try {
            synthesisSlots.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AudioGenerationException("Interrupted waiting for a synthesis slot");
        }
        try {
            List<CaptionedSpeech> parts = new ArrayList<>(chunks.size());
            for (NarrationChunk chunk : chunks) {
                CaptionedSpeech part = speechModel.call(SpokenAcronyms.pin(chunk.text()));
                if (part == null || part.audio() == null || part.audio().length == 0) {
                    throw new AudioGenerationException("Speech model returned no audio for part %d of %d"
                            .formatted(parts.size() + 1, chunks.size()));
                }
                parts.add(part);
                log.debug("Synthesised part {} of {}", parts.size(), chunks.size());
            }
            if (parts.isEmpty()) {
                return new GeneratedAudio(new byte[0], new WordTimings(WordTimings.CURRENT_VERSION, 0, List.of()));
            }

            Mp3Join.Joined joined = Mp3Join.joinWithDurations(
                    parts.stream().map(CaptionedSpeech::audio).toList());
            List<WordTiming> words = new ArrayList<>();
            double offset = 0;
            for (int index = 0; index < parts.size(); index++) {
                words.addAll(TranscriptAligner.align(
                        summary, chunks.get(index), parts.get(index).captions(), offset));
                offset += joined.partDurations().get(index);
            }
            return new GeneratedAudio(joined.bytes(),
                    new WordTimings(WordTimings.CURRENT_VERSION, offset, words));
        } finally {
            synthesisSlots.release();
        }
    }

    private byte[] serialise(WordTimings timings) {
        try {
            return JSON.writeValueAsBytes(timings);
        } catch (JsonProcessingException e) {
            throw new AudioGenerationException("Could not serialise word timestamps", e);
        }
    }

    /** Both artifacts are keyed by bookmark, so reprocessing overwrites rather than orphaning. */
    private String storageKey(Bookmark bookmark) {
        return "audio/" + bookmark.getId() + ".mp3";
    }

    private record GeneratedAudio(byte[] mp3, WordTimings timings) {
    }
}

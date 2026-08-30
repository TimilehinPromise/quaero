package org.timmy.knowcommute.audio;

import java.util.concurrent.Semaphore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.stereotype.Component;
import org.timmy.knowcommute.bookmark.Bookmark;
import org.timmy.knowcommute.bookmark.BookmarkStatus;
import org.timmy.knowcommute.pipeline.PipelineStep;

@Slf4j
@Component
public class AudioGenerationStep implements PipelineStep {

    private final TextToSpeechModel speechModel;
    private final AudioStorage storage;
    private final Semaphore synthesisSlots;

    public AudioGenerationStep(TextToSpeechModel speechModel, AudioStorage storage,
                               TtsProperties properties) {
        this.speechModel = speechModel;
        this.storage = storage;
        this.synthesisSlots = new Semaphore(Math.max(properties.maxConcurrent(), 1), true);
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

        byte[] mp3 = synthesise(summary);
        if (mp3 == null || mp3.length == 0) {
            throw new AudioGenerationException("Speech model returned no audio");
        }

        String key = storage.store(storageKey(bookmark), mp3);
        bookmark.applyAudio(key, Mp3Duration.seconds(mp3));
    }

    /**
     * Synthesis is the heaviest thing this application does — a self-hosted model holds well
     * over a gigabyte per request and runs slower than realtime. The pipeline's own concurrency
     * governs cheap steps like fetching; letting it govern this one too is what takes the
     * speech server down.
     */
    private byte[] synthesise(String summary) {
        try {
            synthesisSlots.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AudioGenerationException("Interrupted waiting for a synthesis slot");
        }
        try {
            return speechModel.call(summary);
        } finally {
            synthesisSlots.release();
        }
    }

    /** Keyed by bookmark, so a reprocessed bookmark overwrites rather than orphaning an object. */
    private String storageKey(Bookmark bookmark) {
        return "audio/" + bookmark.getId() + ".mp3";
    }
}

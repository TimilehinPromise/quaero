package org.timmy.knowcommute.audio;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.stereotype.Component;
import org.timmy.knowcommute.bookmark.Bookmark;
import org.timmy.knowcommute.bookmark.BookmarkStatus;
import org.timmy.knowcommute.pipeline.PipelineStep;

@Slf4j
@Component
@RequiredArgsConstructor
public class AudioGenerationStep implements PipelineStep {

    private final TextToSpeechModel speechModel;
    private final AudioStorage storage;

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

        byte[] mp3 = speechModel.call(summary);
        if (mp3 == null || mp3.length == 0) {
            throw new AudioGenerationException("Speech model returned no audio");
        }

        String key = storage.store(storageKey(bookmark), mp3);
        bookmark.applyAudio(key, Mp3Duration.seconds(mp3));
    }

    /** Keyed by bookmark, so a reprocessed bookmark overwrites rather than orphaning an object. */
    private String storageKey(Bookmark bookmark) {
        return "audio/" + bookmark.getId() + ".mp3";
    }
}

package org.timmy.knowcommute.audio;

import java.util.List;

public record CaptionedSpeech(byte[] audio, List<SpeechCaption> captions) {

    public CaptionedSpeech {
        captions = List.copyOf(captions);
    }
}

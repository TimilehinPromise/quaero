package org.timmy.knowcommute.audio;

import java.util.List;

public record WordTimings(int version, double duration, List<WordTiming> words) {

    public static final int CURRENT_VERSION = 1;

    public WordTimings {
        words = List.copyOf(words);
    }
}

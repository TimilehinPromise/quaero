package org.timmy.knowcommute.audio;

/** Reads playback length from MP3 frame headers, avoiding an audio library for one number. */
final class Mp3Duration {

    private Mp3Duration() {
    }

    static int seconds(byte[] mp3) {
        double total = 0;
        int index = Id3Tags.afterHeader(mp3);
        int end = Id3Tags.beforeTrailer(mp3);
        while (index + 4 <= end) {
            Mp3FrameHeader frame = Mp3FrameHeader.at(mp3, index);
            if (frame == null) {
                index++;
                continue;
            }
            total += frame.seconds();
            index += frame.frameLength();
        }
        return (int) Math.round(total);
    }
}

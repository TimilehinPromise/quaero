package org.timmy.knowcommute.audio;

import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * Concatenates separately synthesised MP3 parts into one playable stream.
 *
 * <p>MP3 frames are self-contained, so parts join without decoding — but only the audio frames
 * may be copied. Each part arrives as a complete file: an ID3v2 header, often a Xing or Info
 * frame whose frame count and duration describe that part alone, the audio, and sometimes an
 * ID3v1 trailer. Copied verbatim, those tags land mid-stream where a decoder reads them as
 * audio, and the stale Xing counts make players report the wrong length and seek to the wrong
 * place.
 *
 * <p>What is left at each seam is the encoder delay and end padding of one part — tens of
 * milliseconds. {@link NarrationChunker} puts the cuts on sentence breaks, where a gap that
 * short disappears into the pause the narration already has.
 */
final class Mp3Join {

    private static final byte[] XING = {'X', 'i', 'n', 'g'};
    private static final byte[] INFO = {'I', 'n', 'f', 'o'};

    private Mp3Join() {
    }

    static byte[] join(List<byte[]> parts) {
        if (parts.size() == 1) {
            return parts.getFirst();
        }

        var joined = new ByteArrayOutputStream();
        Mp3FrameHeader format = null;
        for (byte[] part : parts) {
            int start = firstAudioFrame(part);
            int end = Id3Tags.beforeTrailer(part);
            Mp3FrameHeader header = Mp3FrameHeader.at(part, start);
            if (header == null || start >= end) {
                throw new AudioGenerationException("Speech model returned a part with no audio");
            }
            if (format == null) {
                format = header;
            } else if (!format.sameFormatAs(header)) {
                throw new AudioGenerationException(
                        "Speech model changed audio format mid-narration; parts cannot be joined");
            }
            joined.write(part, start, end - start);
        }
        return joined.toByteArray();
    }

    /**
     * Past the ID3v2 header and past a leading Xing or Info frame. That frame decodes to
     * silence, so dropping it costs nothing; keeping even the first part's copy would leave the
     * joined file claiming one part's duration.
     */
    private static int firstAudioFrame(byte[] mp3) {
        int index = Mp3FrameHeader.seek(mp3, Id3Tags.afterHeader(mp3));
        if (index < 0) {
            return mp3.length;
        }
        Mp3FrameHeader header = Mp3FrameHeader.at(mp3, index);
        if (startsTag(mp3, index + header.xingTagOffset())) {
            int afterTagFrame = index + header.frameLength();
            return Mp3FrameHeader.at(mp3, afterTagFrame) == null ? mp3.length : afterTagFrame;
        }
        return index;
    }

    private static boolean startsTag(byte[] mp3, int index) {
        return matches(mp3, index, XING) || matches(mp3, index, INFO);
    }

    private static boolean matches(byte[] mp3, int index, byte[] tag) {
        if (index < 0 || index + tag.length > mp3.length) {
            return false;
        }
        for (int i = 0; i < tag.length; i++) {
            if (mp3[index + i] != tag[i]) {
                return false;
            }
        }
        return true;
    }
}

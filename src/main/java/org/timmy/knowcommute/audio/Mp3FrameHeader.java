package org.timmy.knowcommute.audio;

/**
 * The four bytes that begin one MPEG audio frame, decoded.
 *
 * <p>MPEG-1 and MPEG-2 Layer III differ in both the bitrate table and the samples per frame,
 * and Kokoro emits 24 kHz — which is MPEG-2. Reading an MPEG-2 stream with MPEG-1 tables
 * yields a frame length ~1.75x too long and reports roughly half the true duration.
 */
record Mp3FrameHeader(int version, int sampleRate, int channelMode, int samplesPerFrame,
                      int frameLength) {

    private static final int[] BITRATES_V1 = {
            0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0};
    private static final int[] BITRATES_V2 = {
            0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0};

    private static final int[] SAMPLE_RATES_V1 = {44100, 48000, 32000, 0};
    private static final int[] SAMPLE_RATES_V2 = {22050, 24000, 16000, 0};
    private static final int[] SAMPLE_RATES_V25 = {11025, 12000, 8000, 0};

    private static final int MPEG_25 = 0;
    private static final int MPEG_RESERVED = 1;
    private static final int MPEG_2 = 2;
    private static final int MPEG_1 = 3;

    private static final int MONO = 3;

    /** Null when {@code index} does not begin a frame whose length this can compute. */
    static Mp3FrameHeader at(byte[] mp3, int index) {
        if (index < 0 || index + 4 > mp3.length) {
            return null;
        }
        if ((mp3[index] & 0xFF) != 0xFF || (mp3[index + 1] & 0xE0) != 0xE0) {
            return null;
        }

        int version = (mp3[index + 1] >> 3) & 0x03;
        if (version == MPEG_RESERVED) {
            return null;
        }

        int bitrate = bitrateTable(version)[(mp3[index + 2] & 0xF0) >> 4] * 1000;
        int sampleRate = sampleRateTable(version)[(mp3[index + 2] & 0x0C) >> 2];
        if (bitrate == 0 || sampleRate == 0) {
            return null;
        }

        int samplesPerFrame = version == MPEG_1 ? 1152 : 576;
        int padding = (mp3[index + 2] & 0x02) >> 1;
        int frameLength = (samplesPerFrame / 8) * bitrate / sampleRate + padding;
        if (frameLength <= 0) {
            return null;
        }

        int channelMode = (mp3[index + 3] & 0xC0) >> 6;
        return new Mp3FrameHeader(version, sampleRate, channelMode, samplesPerFrame, frameLength);
    }

    /** Index of the first frame at or after {@code from}, or -1 when the stream holds none. */
    static int seek(byte[] mp3, int from) {
        for (int index = Math.max(from, 0); index + 4 <= mp3.length; index++) {
            if (at(mp3, index) != null) {
                return index;
            }
        }
        return -1;
    }

    double seconds() {
        return (double) samplesPerFrame / sampleRate;
    }

    /**
     * Offset, from the start of this frame, at which a Xing or Info tag sits when the frame
     * carries one. The tag follows the side-information block, which is a different size per
     * version and channel mode, so its position is not fixed.
     */
    int xingTagOffset() {
        if (version == MPEG_1) {
            return channelMode == MONO ? 21 : 36;
        }
        return channelMode == MONO ? 13 : 21;
    }

    /** Frames disagreeing on any of these cannot be concatenated into one playable stream. */
    boolean sameFormatAs(Mp3FrameHeader other) {
        return version == other.version
                && sampleRate == other.sampleRate
                && channelMode == other.channelMode;
    }

    private static int[] bitrateTable(int version) {
        return version == MPEG_1 ? BITRATES_V1 : BITRATES_V2;
    }

    private static int[] sampleRateTable(int version) {
        return switch (version) {
            case MPEG_1 -> SAMPLE_RATES_V1;
            case MPEG_2 -> SAMPLE_RATES_V2;
            case MPEG_25 -> SAMPLE_RATES_V25;
            default -> new int[] {0, 0, 0, 0};
        };
    }
}

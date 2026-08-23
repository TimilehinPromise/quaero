package org.timmy.knowcommute.audio;

/**
 * Reads playback length from MP3 frame headers, avoiding an audio library for one number.
 *
 * <p>MPEG-1 and MPEG-2 Layer III differ in both the bitrate table and the samples per frame,
 * and Kokoro emits 24 kHz — which is MPEG-2. Reading an MPEG-2 stream with MPEG-1 tables
 * yields a frame length ~1.75x too long and reports roughly half the true duration.
 */
final class Mp3Duration {

    private static final int[] BITRATES_V1 = {
            0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0};
    private static final int[] BITRATES_V2 = {
            0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0};

    private static final int[] SAMPLE_RATES_V1 = {44100, 48000, 32000, 0};
    private static final int[] SAMPLE_RATES_V2 = {22050, 24000, 16000, 0};
    private static final int[] SAMPLE_RATES_V25 = {11025, 12000, 8000, 0};

    private static final int MPEG_25 = 0;
    private static final int MPEG_2 = 2;
    private static final int MPEG_1 = 3;

    private Mp3Duration() {
    }

    static int seconds(byte[] mp3) {
        double total = 0;
        int i = skipId3(mp3);
        while (i + 4 <= mp3.length) {
            if ((mp3[i] & 0xFF) != 0xFF || (mp3[i + 1] & 0xE0) != 0xE0) {
                i++;
                continue;
            }

            int version = (mp3[i + 1] >> 3) & 0x03;
            int bitrate = bitrateTable(version)[(mp3[i + 2] & 0xF0) >> 4] * 1000;
            int sampleRate = sampleRateTable(version)[(mp3[i + 2] & 0x0C) >> 2];
            if (version == 1 || bitrate == 0 || sampleRate == 0) {
                i++;
                continue;
            }

            int samplesPerFrame = version == MPEG_1 ? 1152 : 576;
            int padding = (mp3[i + 2] & 0x02) >> 1;
            int frameLength = (samplesPerFrame / 8) * bitrate / sampleRate + padding;
            if (frameLength <= 0) {
                i++;
                continue;
            }

            total += (double) samplesPerFrame / sampleRate;
            i += frameLength;
        }
        return (int) Math.round(total);
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

    private static int skipId3(byte[] mp3) {
        if (mp3.length < 10 || mp3[0] != 'I' || mp3[1] != 'D' || mp3[2] != '3') {
            return 0;
        }
        int size = ((mp3[6] & 0x7F) << 21) | ((mp3[7] & 0x7F) << 14)
                | ((mp3[8] & 0x7F) << 7) | (mp3[9] & 0x7F);
        return 10 + size;
    }
}

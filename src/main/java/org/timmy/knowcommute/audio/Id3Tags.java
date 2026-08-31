package org.timmy.knowcommute.audio;

/** Locates the audio inside an MP3 file, past whatever metadata the encoder wrapped it in. */
final class Id3Tags {

    private static final int V2_HEADER_BYTES = 10;
    private static final int V1_TRAILER_BYTES = 128;

    private Id3Tags() {
    }

    /** Index of the first byte after an ID3v2 header, or 0 when the file carries none. */
    static int afterHeader(byte[] mp3) {
        if (mp3.length < V2_HEADER_BYTES || mp3[0] != 'I' || mp3[1] != 'D' || mp3[2] != '3') {
            return 0;
        }
        int size = ((mp3[6] & 0x7F) << 21) | ((mp3[7] & 0x7F) << 14)
                | ((mp3[8] & 0x7F) << 7) | (mp3[9] & 0x7F);
        return V2_HEADER_BYTES + size;
    }

    /**
     * Index one past the last audio byte, excluding an ID3v1 trailer. Only concatenation cares:
     * a trailer at the end of a file is ignored, but one in the middle of a joined stream is
     * read as audio.
     */
    static int beforeTrailer(byte[] mp3) {
        int trailer = mp3.length - V1_TRAILER_BYTES;
        if (trailer > 0 && mp3[trailer] == 'T' && mp3[trailer + 1] == 'A' && mp3[trailer + 2] == 'G') {
            return trailer;
        }
        return mp3.length;
    }
}

package org.timmy.knowcommute.audio;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Where generated narration lives. Keys are stored on the bookmark; URLs are minted per
 * request and expire, so a URL is never persisted.
 */
public interface AudioStorage {

    String store(String key, byte[] mp3);

    void storeTimestamps(String audioKey, byte[] json);

    Optional<byte[]> readTimestamps(String audioKey);

    URI presignedUrl(String key);

    void deleteNarration(String audioKey);

    List<String> audioKeysOlderThan(Instant cutoff);

    static String timestampsKey(String audioKey) {
        return audioKey + ".timestamps.json";
    }
}

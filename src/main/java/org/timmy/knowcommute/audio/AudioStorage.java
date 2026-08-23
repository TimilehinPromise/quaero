package org.timmy.knowcommute.audio;

import java.net.URI;
import java.time.Instant;
import java.util.List;

/**
 * Where generated narration lives. Keys are stored on the bookmark; URLs are minted per
 * request and expire, so a URL is never persisted.
 */
public interface AudioStorage {

    String store(String key, byte[] mp3);

    URI presignedUrl(String key);

    void delete(String key);

    List<String> keysOlderThan(Instant cutoff);
}

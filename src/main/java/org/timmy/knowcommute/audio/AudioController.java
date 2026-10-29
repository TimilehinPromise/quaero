package org.timmy.knowcommute.audio;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.timmy.knowcommute.bookmark.Bookmark;
import org.timmy.knowcommute.bookmark.BookmarkNotFoundException;
import org.timmy.knowcommute.bookmark.BookmarkRepository;
import org.timmy.knowcommute.security.CurrentUser;
import org.timmy.knowcommute.user.DeviceUser;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/audio")
public class AudioController {

    private final BookmarkRepository bookmarks;
    private final AudioStorage storage;
    private final AudioProperties properties;

    /**
     * Redirects to a short-lived presigned URL rather than proxying the bytes, so playback
     * traffic never crosses the VPS.
     */
    @GetMapping("/{bookmarkId}")
    public ResponseEntity<Void> stream(@CurrentUser DeviceUser user, @PathVariable UUID bookmarkId) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(storage.presignedUrl(audioKey(user, bookmarkId)))
                .build();
    }

    /**
     * The same presigned URL as a body, for a browser client: an {@code <audio>} element cannot
     * send the device header, so the redirect above is unusable from one. The expiry is taken
     * before signing, so it errs early rather than promising time the URL does not have.
     */
    @GetMapping("/{bookmarkId}/url")
    public AudioUrl url(@CurrentUser DeviceUser user, @PathVariable UUID bookmarkId) {
        String key = audioKey(user, bookmarkId);
        Instant expiresAt = Instant.now().plus(properties.presignTtl());
        return new AudioUrl(storage.presignedUrl(key), expiresAt);
    }

    @GetMapping(value = "/{bookmarkId}/timestamps", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> timestamps(@CurrentUser DeviceUser user, @PathVariable UUID bookmarkId) {
        byte[] timings = storage.readTimestamps(audioKey(user, bookmarkId))
                .orElseThrow(WordTimingsNotAvailableException::new);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePrivate().immutable())
                .body(timings);
    }

    private String audioKey(DeviceUser user, UUID bookmarkId) {
        Bookmark bookmark = bookmarks.findByIdAndUserIdAndDeletedAtIsNull(bookmarkId, user.getId())
                .orElseThrow(() -> new BookmarkNotFoundException(bookmarkId));

        if (bookmark.getAudioStorageKey() == null) {
            throw new AudioNotReadyException(bookmark.getStatus().name());
        }
        return bookmark.getAudioStorageKey();
    }
}

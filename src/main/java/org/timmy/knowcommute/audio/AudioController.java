package org.timmy.knowcommute.audio;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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

    /**
     * Redirects to a short-lived presigned URL rather than proxying the bytes, so playback
     * traffic never crosses the VPS.
     */
    @GetMapping("/{bookmarkId}")
    public ResponseEntity<Void> stream(@CurrentUser DeviceUser user, @PathVariable UUID bookmarkId) {
        Bookmark bookmark = bookmarks.findByIdAndUserIdAndDeletedAtIsNull(bookmarkId, user.getId())
                .orElseThrow(() -> new BookmarkNotFoundException(bookmarkId));

        if (bookmark.getAudioStorageKey() == null) {
            throw new AudioNotReadyException(bookmark.getStatus().name());
        }

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(storage.presignedUrl(bookmark.getAudioStorageKey()))
                .build();
    }
}

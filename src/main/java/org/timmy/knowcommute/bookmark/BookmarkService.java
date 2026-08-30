package org.timmy.knowcommute.bookmark;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.timmy.knowcommute.user.DeviceUser;

@Service
@RequiredArgsConstructor
public class BookmarkService {

    private final BookmarkRepository bookmarks;
    private final UrlValidator urlValidator;

    @Transactional
    public BookmarkDetail create(DeviceUser user, CreateBookmarkRequest request) {
        URI url = urlValidator.validate(request.url());
        Bookmark bookmark = Bookmark.pending(user, url.toString(), request.summaryLengthOrDefault());
        try {
            return BookmarkDetail.from(bookmarks.saveAndFlush(bookmark));
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateBookmarkException(url.toString());
        }
    }

    @Transactional(readOnly = true)
    public Page<BookmarkListItem> list(DeviceUser user, BookmarkStatus status, Pageable pageable) {
        Page<Bookmark> page = status == null
                ? bookmarks.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(user.getId(), pageable)
                : bookmarks.findByUserIdAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(
                        user.getId(), status, pageable);
        return page.map(BookmarkListItem::from);
    }

    @Transactional(readOnly = true)
    public BookmarkDetail get(DeviceUser user, UUID id) {
        return BookmarkDetail.from(require(user, id));
    }

    /** Recovery path for sites that refuse a server-side fetch; see PRD 13. */
    @Transactional
    public BookmarkDetail pasteContent(DeviceUser user, UUID id, PasteContentRequest request) {
        Bookmark bookmark = require(user, id);
        if (bookmark.getStatus() != BookmarkStatus.BLOCKED
                && bookmark.getStatus() != BookmarkStatus.FAILED) {
            throw new BookmarkNotRecoverableException(bookmark.getStatus());
        }

        String text = request.text().replaceAll("\\s+", " ").trim();
        String title = request.title() == null || request.title().isBlank()
                ? bookmark.getTitle() : request.title().trim();
        bookmark.applyPastedContent(title, text, text.split("\\s+").length, sha256(text));
        return BookmarkDetail.from(bookmark);
    }

    private String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JDK", e);
        }
    }

    /** Retry starts the whole pipeline over; a partial fix may live in any step. */
    @Transactional
    public BookmarkDetail retry(DeviceUser user, UUID id) {
        Bookmark bookmark = require(user, id);
        if (bookmark.getStatus() != BookmarkStatus.FAILED
                && bookmark.getStatus() != BookmarkStatus.BLOCKED) {
            throw new BookmarkNotRecoverableException(bookmark.getStatus());
        }
        bookmark.requeue();
        return BookmarkDetail.from(bookmark);
    }

    @Transactional
    public void delete(DeviceUser user, UUID id) {
        require(user, id).softDelete();
    }

    private Bookmark require(DeviceUser user, UUID id) {
        return bookmarks.findByIdAndUserIdAndDeletedAtIsNull(id, user.getId())
                .orElseThrow(() -> new BookmarkNotFoundException(id));
    }
}

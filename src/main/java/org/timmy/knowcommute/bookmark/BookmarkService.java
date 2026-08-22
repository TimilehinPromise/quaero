package org.timmy.knowcommute.bookmark;

import java.net.URI;
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

    @Transactional
    public void delete(DeviceUser user, UUID id) {
        require(user, id).softDelete();
    }

    private Bookmark require(DeviceUser user, UUID id) {
        return bookmarks.findByIdAndUserIdAndDeletedAtIsNull(id, user.getId())
                .orElseThrow(() -> new BookmarkNotFoundException(id));
    }
}

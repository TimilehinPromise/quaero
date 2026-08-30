package org.timmy.knowcommute.bookmark;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.timmy.knowcommute.security.CurrentUser;
import org.timmy.knowcommute.user.DeviceUser;
import org.timmy.knowcommute.web.PagedResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/bookmarks")
public class BookmarkController {

    private static final int MAX_PAGE_SIZE = 100;

    private final BookmarkService bookmarkService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookmarkDetail create(@CurrentUser DeviceUser user,
                                 @Valid @RequestBody CreateBookmarkRequest request) {
        return bookmarkService.create(user, request);
    }

    @GetMapping
    public PagedResponse<BookmarkListItem> list(
            @CurrentUser DeviceUser user,
            @RequestParam(required = false) BookmarkStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
        return PagedResponse.of(bookmarkService.list(user, status, pageRequest), item -> item);
    }

    @GetMapping("/{id}")
    public BookmarkDetail get(@CurrentUser DeviceUser user, @PathVariable UUID id) {
        return bookmarkService.get(user, id);
    }

    @PostMapping("/{id}/content")
    public BookmarkDetail pasteContent(@CurrentUser DeviceUser user, @PathVariable UUID id,
                                       @Valid @RequestBody PasteContentRequest request) {
        return bookmarkService.pasteContent(user, id, request);
    }

    @PostMapping("/{id}/retry")
    public BookmarkDetail retry(@CurrentUser DeviceUser user, @PathVariable UUID id) {
        return bookmarkService.retry(user, id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUser DeviceUser user, @PathVariable UUID id) {
        bookmarkService.delete(user, id);
    }
}

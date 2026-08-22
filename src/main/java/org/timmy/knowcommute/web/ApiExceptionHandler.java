package org.timmy.knowcommute.web;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.timmy.knowcommute.bookmark.BookmarkNotFoundException;
import org.timmy.knowcommute.bookmark.DuplicateBookmarkException;
import org.timmy.knowcommute.bookmark.InvalidUrlException;
import org.timmy.knowcommute.security.UnauthenticatedException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final String PROBLEM_BASE = "https://readcast.app/problems/";

    @ExceptionHandler(UnauthenticatedException.class)
    public ProblemDetail onUnauthenticated(UnauthenticatedException e) {
        return problem(HttpStatus.UNAUTHORIZED, "unauthenticated", "Not authenticated", e.getMessage());
    }

    @ExceptionHandler(BookmarkNotFoundException.class)
    public ProblemDetail onNotFound(BookmarkNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "bookmark-not-found", "Bookmark not found", e.getMessage());
    }

    @ExceptionHandler(DuplicateBookmarkException.class)
    public ProblemDetail onDuplicate(DuplicateBookmarkException e) {
        return problem(HttpStatus.CONFLICT, "duplicate-bookmark", "Already saved", e.getMessage());
    }

    @ExceptionHandler(InvalidUrlException.class)
    public ProblemDetail onInvalidUrl(InvalidUrlException e) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-url", "Invalid URL", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail onValidationFailure(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .findFirst()
                .orElse("Request body is invalid");
        return problem(HttpStatus.BAD_REQUEST, "invalid-request", "Invalid request", detail);
    }

    private ProblemDetail problem(HttpStatus status, String slug, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(PROBLEM_BASE + slug));
        problem.setTitle(title);
        return problem;
    }
}

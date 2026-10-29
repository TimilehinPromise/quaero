package org.timmy.knowcommute.web;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.timmy.knowcommute.audio.AudioNotReadyException;
import org.timmy.knowcommute.audio.WordTimingsNotAvailableException;
import org.timmy.knowcommute.bookmark.BookmarkNotFoundException;
import org.timmy.knowcommute.bookmark.BookmarkNotRecoverableException;
import org.timmy.knowcommute.bookmark.DuplicateBookmarkException;
import org.timmy.knowcommute.bookmark.InvalidUrlException;
import org.timmy.knowcommute.lens.ListenerProfileMissingException;
import org.timmy.knowcommute.lens.ListenerProfileNotFoundException;
import org.timmy.knowcommute.lens.ProfileCheckUnavailableException;
import org.timmy.knowcommute.lens.ProfileRejectedException;
import org.timmy.knowcommute.security.UnauthenticatedException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private final String problemTypeBase;

    public ApiExceptionHandler(@Value("${readcast.problem-type-base}") String problemTypeBase) {
        this.problemTypeBase = problemTypeBase;
    }

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

    @ExceptionHandler(AudioNotReadyException.class)
    public ProblemDetail onAudioNotReady(AudioNotReadyException e) {
        return problem(HttpStatus.CONFLICT, "audio-not-ready", "Audio not ready", e.getMessage());
    }

    @ExceptionHandler(WordTimingsNotAvailableException.class)
    public ProblemDetail onWordTimingsNotAvailable(WordTimingsNotAvailableException e) {
        return problem(HttpStatus.NOT_FOUND, "word-timestamps-not-found", "Word timestamps not found",
                e.getMessage());
    }

    @ExceptionHandler(BookmarkNotRecoverableException.class)
    public ProblemDetail onNotRecoverable(BookmarkNotRecoverableException e) {
        return problem(HttpStatus.CONFLICT, "not-recoverable", "Bookmark not recoverable", e.getMessage());
    }

    @ExceptionHandler(InvalidUrlException.class)
    public ProblemDetail onInvalidUrl(InvalidUrlException e) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-url", "Invalid URL", e.getMessage());
    }

    @ExceptionHandler(ListenerProfileNotFoundException.class)
    public ProblemDetail onProfileNotFound(ListenerProfileNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "listener-profile-not-found", "No listener profile", e.getMessage());
    }

    @ExceptionHandler(ListenerProfileMissingException.class)
    public ProblemDetail onProfileMissing(ListenerProfileMissingException e) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "listener-profile-missing", "No listener profile",
                e.getMessage());
    }

    @ExceptionHandler(ProfileRejectedException.class)
    public ProblemDetail onProfileRejected(ProfileRejectedException e) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "listener-profile-rejected", "Profile not accepted",
                e.getMessage());
    }

    @ExceptionHandler(ProfileCheckUnavailableException.class)
    public ProblemDetail onProfileCheckUnavailable(ProfileCheckUnavailableException e) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "listener-profile-check-unavailable",
                "Profile check unavailable", "The profile could not be checked right now; try again shortly");
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
        problem.setType(URI.create(problemTypeBase + slug));
        problem.setTitle(title);
        return problem;
    }
}

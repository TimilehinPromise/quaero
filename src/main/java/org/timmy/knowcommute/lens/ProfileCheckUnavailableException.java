package org.timmy.knowcommute.lens;

/** The check could not reach a verdict. Saving anyway would skip the guard, so the save fails. */
public class ProfileCheckUnavailableException extends RuntimeException {

    public ProfileCheckUnavailableException(String message) {
        super(message);
    }

    public ProfileCheckUnavailableException(Throwable cause) {
        super("The profile check is unavailable", cause);
    }
}

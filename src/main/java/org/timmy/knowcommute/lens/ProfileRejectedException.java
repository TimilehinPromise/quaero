package org.timmy.knowcommute.lens;

public class ProfileRejectedException extends RuntimeException {

    public ProfileRejectedException(ProfileCheck.Verdict verdict) {
        super(verdict.reason().isBlank()
                ? "This doesn't read as a description of you (" + verdict.outcome().name().toLowerCase(java.util.Locale.ROOT) + ")"
                : verdict.reason());
    }
}

package org.timmy.knowcommute.lens;

public class ListenerProfileMissingException extends RuntimeException {

    public ListenerProfileMissingException() {
        super("A lensed summary was asked for, but no listener profile is set");
    }
}

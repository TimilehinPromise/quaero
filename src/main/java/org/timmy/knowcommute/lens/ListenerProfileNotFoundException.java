package org.timmy.knowcommute.lens;

public class ListenerProfileNotFoundException extends RuntimeException {

    public ListenerProfileNotFoundException() {
        super("No listener profile is set");
    }
}

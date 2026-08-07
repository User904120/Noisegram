package org.cleargram.api;

import java.util.Objects;

/**
 * Minimal public message model for Cleargram Core.
 */
public final class NoiseMessage {

    private final String text;

    public NoiseMessage(String text) {
        this.text = Objects.requireNonNull(text, "text");
    }

    public String getText() {
        return text;
    }
}

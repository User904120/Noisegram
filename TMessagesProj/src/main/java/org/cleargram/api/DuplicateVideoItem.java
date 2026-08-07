package org.cleargram.api;

import java.util.Arrays;
import java.util.Objects;

/** Immutable Duplicate Video item with an opaque presentation identity. */
public final class DuplicateVideoItem {

    private static final int MATCH_KEY_LENGTH = 32;

    private final long presentationItemId;
    private final byte[] matchKey;

    public DuplicateVideoItem(long presentationItemId, byte[] matchKey) {
        Objects.requireNonNull(matchKey, "matchKey");
        if (matchKey.length != MATCH_KEY_LENGTH) {
            throw new IllegalArgumentException("matchKey must contain 32 bytes");
        }
        this.presentationItemId = presentationItemId;
        this.matchKey = Arrays.copyOf(matchKey, matchKey.length);
    }

    public long getPresentationItemId() {
        return presentationItemId;
    }

    public byte[] getMatchKey() {
        return Arrays.copyOf(matchKey, matchKey.length);
    }
}

package org.cleargram.internal;

import java.util.Objects;

import org.cleargram.spi.DuplicateVideoReadableSource;

/** Immutable internal hashing input with an opaque presentation identity. */
public final class DuplicateVideoHashingItem {

    private final long presentationItemId;
    private final DuplicateVideoReadableSource source;

    public DuplicateVideoHashingItem(long presentationItemId, DuplicateVideoReadableSource source) {
        this.presentationItemId = presentationItemId;
        this.source = Objects.requireNonNull(source, "source");
    }

    public long getPresentationItemId() {
        return presentationItemId;
    }

    public DuplicateVideoReadableSource getSource() {
        return source;
    }
}

package org.cleargram.integration;

import java.util.Objects;

import org.cleargram.spi.DuplicateVideoReadableSource;

/** Immutable local-source resolution result. */
final class TelegramDuplicateVideoSourceResolution {

    enum Status { UNSUPPORTED, LOCAL_UNAVAILABLE, LOCAL_AVAILABLE }

    private static final TelegramDuplicateVideoSourceResolution UNSUPPORTED =
            new TelegramDuplicateVideoSourceResolution(Status.UNSUPPORTED, null);
    private static final TelegramDuplicateVideoSourceResolution LOCAL_UNAVAILABLE =
            new TelegramDuplicateVideoSourceResolution(Status.LOCAL_UNAVAILABLE, null);

    private final Status status;
    private final DuplicateVideoReadableSource source;

    private TelegramDuplicateVideoSourceResolution(Status status, DuplicateVideoReadableSource source) {
        this.status = status;
        this.source = source;
    }

    static TelegramDuplicateVideoSourceResolution unsupported() { return UNSUPPORTED; }
    static TelegramDuplicateVideoSourceResolution localUnavailable() { return LOCAL_UNAVAILABLE; }
    static TelegramDuplicateVideoSourceResolution localAvailable(DuplicateVideoReadableSource source) {
        return new TelegramDuplicateVideoSourceResolution(Status.LOCAL_AVAILABLE,
                Objects.requireNonNull(source, "source"));
    }
    Status getStatus() { return status; }
    DuplicateVideoReadableSource getSourceOrNull() { return source; }
}

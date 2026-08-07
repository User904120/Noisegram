package org.cleargram.integration;

import java.io.File;
import java.util.Objects;

/** Immutable Telegram-free description of one already selected local video source. */
final class TelegramDuplicateVideoMediaSourceDescriptor {

    private final File file;
    private final long expectedSize;

    TelegramDuplicateVideoMediaSourceDescriptor(File file, long expectedSize) {
        File sourceFile = Objects.requireNonNull(file, "file");
        if (expectedSize <= 0) {
            throw new IllegalArgumentException("expectedSize must be positive");
        }
        this.file = new File(sourceFile.getPath());
        this.expectedSize = expectedSize;
    }

    File getFile() {
        return file;
    }

    long getExpectedSize() {
        return expectedSize;
    }
}

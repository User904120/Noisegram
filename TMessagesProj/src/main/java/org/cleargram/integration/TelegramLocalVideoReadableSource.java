package org.cleargram.integration;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import org.cleargram.spi.DuplicateVideoReadableSource;

/** Readable source for a verified Telegram-managed local video file. */
final class TelegramLocalVideoReadableSource implements DuplicateVideoReadableSource {

    private final File file;
    private final long expectedSize;

    TelegramLocalVideoReadableSource(File file, long expectedSize) {
        this.file = Objects.requireNonNull(file, "file");
        if (expectedSize <= 0) throw new IllegalArgumentException("expectedSize must be positive");
        this.expectedSize = expectedSize;
    }

    @Override
    public InputStream openStream() throws IOException {
        verifyFile(file, expectedSize);
        return new ExactLengthInputStream(new FileInputStream(file), expectedSize);
    }

    static void verifyFile(File file, long expectedSize) throws IOException {
        if (!file.exists() || !file.isFile() || !file.canRead() || file.length() != expectedSize) {
            throw new IOException("Telegram local video is unavailable");
        }
    }
}

/** Input stream which verifies exact content length at EOF. */
final class ExactLengthInputStream extends InputStream {

    private final InputStream input;
    private final long expectedSize;
    private long readCount;
    private boolean checkedEnd;

    ExactLengthInputStream(InputStream input, long expectedSize) {
        this.input = Objects.requireNonNull(input, "input");
        this.expectedSize = expectedSize;
    }

    @Override public int read() throws IOException {
        byte[] single = new byte[1];
        return read(single, 0, 1) == -1 ? -1 : single[0] & 0xff;
    }

    @Override public int read(byte[] buffer, int offset, int length) throws IOException {
        if (buffer == null) throw new NullPointerException("buffer");
        if (offset < 0 || length < 0 || length > buffer.length - offset) {
            throw new IndexOutOfBoundsException();
        }
        if (length == 0) return 0;
        if (readCount == expectedSize) return verifyEnd();
        int requested = (int) Math.min(length, expectedSize - readCount);
        int count = input.read(buffer, offset, requested);
        if (count == -1) throw new IOException("Telegram local video is shorter than expected");
        readCount += count;
        return count;
    }

    private int verifyEnd() throws IOException {
        if (!checkedEnd) {
            checkedEnd = true;
            if (input.read() != -1) throw new IOException("Telegram local video is longer than expected");
        }
        return -1;
    }

    @Override public long skip(long count) throws IOException {
        long skipped = 0;
        while (skipped < count && read() != -1) skipped++;
        return skipped;
    }
    @Override public boolean markSupported() { return false; }
    @Override public void close() throws IOException { input.close(); }
}

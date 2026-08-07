package org.cleargram.integration;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

import org.junit.Test;

public final class TelegramLocalVideoReadableSourceTest {

    @Test
    public void constructorAndFreshStreamsValidateFileState() throws Exception {
        File file = createFile(new byte[] { 1, 2, 3 });
        try {
            expectNull(() -> new TelegramLocalVideoReadableSource(null, 1));
            expectIllegal(() -> new TelegramLocalVideoReadableSource(file, 0));
            expectIllegal(() -> new TelegramLocalVideoReadableSource(file, -1));
            TelegramLocalVideoReadableSource source = new TelegramLocalVideoReadableSource(file, 3);
            InputStream first = source.openStream();
            InputStream second = source.openStream();
            assertNotSame(first, second);
            assertArrayEquals(new byte[] { 1, 2, 3 }, readAll(first));
            assertArrayEquals(new byte[] { 1, 2, 3 }, readAll(second));
            first.close();
            second.close();
        } finally { file.delete(); }
    }

    @Test
    public void openRejectsDeletedResizedAndDirectoryReplacement() throws Exception {
        File file = createFile(new byte[] { 1, 2, 3 });
        TelegramLocalVideoReadableSource source = new TelegramLocalVideoReadableSource(file, 3);
        assertTrue(file.delete());
        expectIo(source::openStream);

        file = createFile(new byte[] { 1, 2, 3 });
        source = new TelegramLocalVideoReadableSource(file, 3);
        write(file, new byte[] { 1, 2 });
        expectIo(source::openStream);
        write(file, new byte[] { 1, 2, 3, 4 });
        expectIo(source::openStream);
        assertTrue(file.delete());
        assertTrue(file.mkdir());
        expectIo(source::openStream);
        file.delete();
    }

    @Test
    public void exactLengthStreamDetectsShortExtraAndSupportsStandardReads() throws Exception {
        expectIo(() -> readAll(new ExactLengthInputStream(new ByteArrayInputStream(new byte[] { 1 }), 2)));
        expectIo(() -> readAll(new ExactLengthInputStream(new ByteArrayInputStream(new byte[] { 1, 2, 3 }), 2)));
        ExactLengthInputStream exact = new ExactLengthInputStream(new ByteArrayInputStream(new byte[] { 1, 2 }), 2);
        assertTrue(exact.read(new byte[1], 0, 0) == 0);
        assertTrue(exact.read() == 1);
        byte[] remaining = new byte[1];
        assertTrue(exact.read(remaining) == 1);
        assertTrue(remaining[0] == 2);
        assertTrue(exact.read() == -1);
        assertFalse(exact.markSupported());
        exact.close();

        ExactLengthInputStream skipped = new ExactLengthInputStream(new ByteArrayInputStream(new byte[] { 1, 2, 3 }), 3);
        assertTrue(skipped.skip(0) == 0);
        assertTrue(skipped.skip(-1) == 0);
        assertTrue(skipped.skip(2) == 2);
        assertTrue(skipped.read() == 3);
        assertTrue(skipped.read() == -1);
        skipped.close();
    }

    @Test
    public void exactLengthStreamPropagatesCloseFailureAndDoesNotCatchErrors() throws Exception {
        InputStream closeFailure = new ByteArrayInputStream(new byte[] { 1 }) {
            @Override public void close() throws IOException { throw new IOException("close"); }
        };
        ExactLengthInputStream stream = new ExactLengthInputStream(closeFailure, 1);
        expectIo(stream::close);
        try {
            readAll(new ExactLengthInputStream(new InputStream() {
                @Override public int read() { throw new AssertionError("fatal"); }
            }, 1));
            fail("Expected Error");
        } catch (AssertionError expected) { }
    }

    @Test
    public void publicSourceDoesNotExposeFileOrPathGetter() {
        for (java.lang.reflect.Method method : TelegramLocalVideoReadableSource.class.getMethods()) {
            if (method.getName().startsWith("get")) {
                assertFalse(method.getReturnType() == File.class || method.getReturnType() == String.class
                        || method.getReturnType().getName().equals("java.net.URI"));
            }
        }
    }

    @Test
    public void openedStreamDetectsTruncationAndClosesAfterError() throws Exception {
        File file = createFile(new byte[] { 1, 2, 3 });
        try {
            TelegramLocalVideoReadableSource source = new TelegramLocalVideoReadableSource(file, 3);
            InputStream opened = source.openStream();
            write(file, new byte[] { 1 });
            expectIo(() -> readAll(opened));
        } finally { file.delete(); }

        final boolean[] closed = { false };
        try (InputStream stream = new ExactLengthInputStream(new InputStream() {
            @Override public int read() { throw new AssertionError("fatal"); }
            @Override public void close() { closed[0] = true; }
        }, 1)) {
            stream.read();
            fail("Expected Error");
        } catch (AssertionError expected) { }
        assertTrue(closed[0]);
    }

    private static File createFile(byte[] bytes) throws IOException {
        File file = Files.createTempFile("cleargram_video_", ".bin").toFile();
        write(file, bytes);
        return file;
    }
    private static void write(File file, byte[] bytes) throws IOException {
        try (FileOutputStream output = new FileOutputStream(file, false)) { output.write(bytes); }
    }
    private static byte[] readAll(InputStream input) throws IOException {
        try (InputStream stream = input) {
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[2];
            int count;
            while ((count = stream.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toByteArray();
        }
    }
    private interface IoAction { void run() throws IOException; }
    private static void expectIo(IoAction action) {
        try { action.run(); fail("Expected IOException"); } catch (IOException expected) { }
    }
    private static void expectNull(Runnable action) {
        try { action.run(); fail("Expected NullPointerException"); } catch (NullPointerException expected) { }
    }
    private static void expectIllegal(Runnable action) {
        try { action.run(); fail("Expected IllegalArgumentException"); } catch (IllegalArgumentException expected) { }
    }
}

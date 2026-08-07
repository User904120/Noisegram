package org.cleargram.internal;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.cleargram.api.DuplicateVideoBatch;
import org.cleargram.api.DuplicateVideoMatchMode;
import org.cleargram.spi.DuplicateVideoReadableSource;

public final class DuplicateVideoHashingComponentTest {

    @Test
    public void validatesEntryPointAndFactoryFailureFailsOpen() {
        DuplicateVideoHashingComponent component = new DuplicateVideoHashingComponent();
        expectNull(() -> component.hash(null));
        expectNull(() -> new DuplicateVideoHashingComponent(null));

        DuplicateVideoHashingResult failed = new DuplicateVideoHashingComponent(() -> null).hash(request(
                DuplicateVideoMatchMode.VIDEO, "", false, item(1, bytes("x"))));
        assertFalse(failed.hasBatch());
        assertEquals(Collections.singletonList(1L), failed.getFailedPresentationItemIds());
    }

    @Test
    public void videoHashesFullChunkedAndEmptyStreamsWithRawSha256() {
        DuplicateVideoHashingComponent component = new DuplicateVideoHashingComponent();
        DuplicateVideoHashingResult abc = component.hash(request(DuplicateVideoMatchMode.VIDEO, "ignored", false,
                item(1, new ChunkedInputStream(bytes("abc"), 1))));
        assertArrayEquals(hex("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"),
                abc.getBatchOrNull().getItems().get(0).getMatchKey());

        DuplicateVideoHashingResult empty = component.hash(request(DuplicateVideoMatchMode.VIDEO, "", false,
                item(2, new byte[0])));
        assertArrayEquals(hex("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"),
                empty.getBatchOrNull().getItems().get(0).getMatchKey());
    }

    @Test
    public void videoModeIgnoresTextAndVideoAndTextUsesApprovedEnvelope() {
        DuplicateVideoHashingComponent component = new DuplicateVideoHashingComponent();
        byte[] videoKeyA = component.hash(request(DuplicateVideoMatchMode.VIDEO, "caption", false,
                item(1, bytes("video")))).getBatchOrNull().getItems().get(0).getMatchKey();
        byte[] videoKeyB = component.hash(request(DuplicateVideoMatchMode.VIDEO, "different", false,
                item(1, bytes("video")))).getBatchOrNull().getItems().get(0).getMatchKey();
        assertArrayEquals(videoKeyA, videoKeyB);

        byte[] compound = component.hash(request(DuplicateVideoMatchMode.VIDEO_AND_TEXT, "caption", false,
                item(1, bytes("video")))).getBatchOrNull().getItems().get(0).getMatchKey();
        assertArrayEquals(hex("0f858430d880d2aab025b45f5e9ec29171a87273fe319cd4ba83f10fd3e50dbb"), compound);
    }

    @Test
    public void videoAndTextPreservesExactTextAndHashesSharedTextOnce() {
        DuplicateVideoHashingComponent component = new DuplicateVideoHashingComponent();
        assertArrayEquals(key(component, ""), key(component, null));
        assertDifferent(key(component, "caption"), key(component, "Caption"));
        assertDifferent(key(component, "caption"), key(component, " caption "));
        assertDifferent(key(component, "\u00e9"), key(component, "e\u0301"));
        assertDifferent(key(component, "caption"), key(component, "caption\n"));

        AtomicInteger creates = new AtomicInteger();
        DuplicateVideoHashingComponent counting = new DuplicateVideoHashingComponent(() -> {
            creates.incrementAndGet();
            return sha256();
        });
        counting.hash(request(DuplicateVideoMatchMode.VIDEO_AND_TEXT, "caption", false,
                item(1, bytes("one")), item(2, bytes("two"))));
        assertEquals(5, creates.get());
    }

    @Test
    public void processesSourcesSequentiallyClosesStreamsAndContinuesAfterFailure() {
        List<String> events = new ArrayList<>();
        DuplicateVideoHashingItem first = new DuplicateVideoHashingItem(1L,
                trackingSource("one", bytes("one"), events, false));
        DuplicateVideoHashingItem failed = new DuplicateVideoHashingItem(2L,
                () -> { throw new IOException("unreadable"); });
        DuplicateVideoHashingItem third = new DuplicateVideoHashingItem(3L,
                trackingSource("three", bytes("three"), events, false));

        DuplicateVideoHashingResult result = new DuplicateVideoHashingComponent().hash(request(
                DuplicateVideoMatchMode.VIDEO, "", false, first, failed, third));
        DuplicateVideoBatch batch = result.getBatchOrNull();
        assertEquals(Arrays.asList(1L, 3L), ids(batch));
        assertEquals(Collections.singletonList(2L), result.getFailedPresentationItemIds());
        assertTrue(batch.hasOtherVisibleMedia());
        assertEquals(Arrays.asList("open one", "read one", "close one", "open three", "read three", "close three"), events);
    }

    @Test
    public void closeFailureAndAllFailedRemainFailOpenAndSourcesClose() {
        CloseFailingInputStream stream = new CloseFailingInputStream(bytes("data"));
        DuplicateVideoHashingResult closeFailure = new DuplicateVideoHashingComponent().hash(request(
                DuplicateVideoMatchMode.VIDEO, "", false,
                new DuplicateVideoHashingItem(1L, () -> stream)));
        assertTrue(stream.closed);
        assertFalse(closeFailure.hasBatch());
        assertEquals(Collections.singletonList(1L), closeFailure.getFailedPresentationItemIds());

        DuplicateVideoHashingResult allFailed = new DuplicateVideoHashingComponent().hash(request(
                DuplicateVideoMatchMode.VIDEO, "", false,
                new DuplicateVideoHashingItem(2L, () -> { throw new RuntimeException("failure"); }),
                new DuplicateVideoHashingItem(3L, () -> { throw new IOException("failure"); })));
        assertFalse(allFailed.hasBatch());
        assertEquals(Arrays.asList(2L, 3L), allFailed.getFailedPresentationItemIds());
    }

    @Test
    public void readFailuresCloseExactlyOnceAndDoNotStopLaterItems() {
        CountingSource first = successfulSource(bytes("first"));
        ReadFailingSource ioFailure = new ReadFailingSource(false);
        CountingSource third = successfulSource(bytes("third"));
        DuplicateVideoHashingResult result = new DuplicateVideoHashingComponent().hash(request(
                DuplicateVideoMatchMode.VIDEO, "", false,
                new DuplicateVideoHashingItem(1L, first),
                new DuplicateVideoHashingItem(2L, ioFailure),
                new DuplicateVideoHashingItem(3L, third)));

        assertEquals(Arrays.asList(1L, 3L), ids(result.getBatchOrNull()));
        assertEquals(Collections.singletonList(2L), result.getFailedPresentationItemIds());
        assertTrue(result.getBatchOrNull().hasOtherVisibleMedia());
        assertEquals(1, first.opens);
        assertEquals(1, ioFailure.opens);
        assertTrue(ioFailure.closed);
        assertEquals(1, third.opens);

        ReadFailingSource runtimeFailure = new ReadFailingSource(true);
        DuplicateVideoHashingResult runtimeResult = new DuplicateVideoHashingComponent().hash(request(
                DuplicateVideoMatchMode.VIDEO, "", false,
                new DuplicateVideoHashingItem(4L, runtimeFailure),
                new DuplicateVideoHashingItem(5L, successfulSource(bytes("next")))));
        assertEquals(Collections.singletonList(4L), runtimeResult.getFailedPresentationItemIds());
        assertEquals(Collections.singletonList(5L), ids(runtimeResult.getBatchOrNull()));
        assertTrue(runtimeFailure.closed);
    }

    @Test
    public void nullStreamFailsOnlyItsItemAndEverySourceOpensOnce() {
        CountingSource nullStream = new CountingSource(() -> null);
        CountingSource success = successfulSource(bytes("success"));
        DuplicateVideoHashingResult result = new DuplicateVideoHashingComponent().hash(request(
                DuplicateVideoMatchMode.VIDEO, "", false,
                new DuplicateVideoHashingItem(1L, nullStream),
                new DuplicateVideoHashingItem(2L, success)));

        assertEquals(Collections.singletonList(1L), result.getFailedPresentationItemIds());
        assertEquals(Collections.singletonList(2L), ids(result.getBatchOrNull()));
        assertTrue(result.getBatchOrNull().hasOtherVisibleMedia());
        assertEquals(1, nullStream.opens);
        assertEquals(1, success.opens);
    }

    @Test
    public void invalidDigestLengthsAndDigestRuntimeFailuresArePerItemFailures() {
        DuplicateVideoHashingComponent invalidVideo = new DuplicateVideoHashingComponent(sequenceFactory(
                new FixedDigest(new byte[31]), sha256()));
        DuplicateVideoHashingResult videoResult = invalidVideo.hash(request(DuplicateVideoMatchMode.VIDEO, "", false,
                item(1, bytes("first")), item(2, bytes("second"))));
        assertEquals(Collections.singletonList(1L), videoResult.getFailedPresentationItemIds());
        assertEquals(Collections.singletonList(2L), ids(videoResult.getBatchOrNull()));
        assertTrue(videoResult.getBatchOrNull().hasOtherVisibleMedia());

        CountingSource first = successfulSource(bytes("first"));
        CountingSource second = successfulSource(bytes("second"));
        DuplicateVideoHashingComponent invalidEnvelope = new DuplicateVideoHashingComponent(sequenceFactory(
                sha256(), sha256(), new FixedDigest(new byte[31]), sha256(), sha256()));
        DuplicateVideoHashingResult envelopeResult = invalidEnvelope.hash(request(
                DuplicateVideoMatchMode.VIDEO_AND_TEXT, "text", false,
                new DuplicateVideoHashingItem(3L, first), new DuplicateVideoHashingItem(4L, second)));
        assertEquals(Collections.singletonList(3L), envelopeResult.getFailedPresentationItemIds());
        assertEquals(Collections.singletonList(4L), ids(envelopeResult.getBatchOrNull()));
        assertEquals(1, first.opens);
        assertEquals(1, second.opens);

        DuplicateVideoHashingComponent runtimeDigest = new DuplicateVideoHashingComponent(sequenceFactory(
                new FixedDigest(new IllegalStateException("digest failure")), sha256()));
        DuplicateVideoHashingResult runtimeResult = runtimeDigest.hash(request(DuplicateVideoMatchMode.VIDEO, "", false,
                item(5, bytes("bad")), item(6, bytes("good"))));
        assertEquals(Collections.singletonList(5L), runtimeResult.getFailedPresentationItemIds());
        assertEquals(Collections.singletonList(6L), ids(runtimeResult.getBatchOrNull()));
        assertTrue(runtimeResult.getBatchOrNull().hasOtherVisibleMedia());
    }

    @Test
    public void preservesVisibilityAndDefensivelyReturnsSuccessfulKeys() {
        DuplicateVideoHashingComponent component = new DuplicateVideoHashingComponent();
        assertTrue(component.hash(request(DuplicateVideoMatchMode.VIDEO, "", true,
                item(1, bytes("same")))).getBatchOrNull().hasOtherVisibleMedia());
        DuplicateVideoBatch batch = component.hash(request(DuplicateVideoMatchMode.VIDEO, "", false,
                item(1, bytes("same")))).getBatchOrNull();
        assertFalse(batch.hasOtherVisibleMedia());
        byte[] key = batch.getItems().get(0).getMatchKey();
        byte[] expected = Arrays.copyOf(key, key.length);
        key[0] ^= 1;
        assertArrayEquals(expected, batch.getItems().get(0).getMatchKey());
    }

    @Test
    public void sharedTextDigestFailureDoesNotOpenSourcesAndErrorsEscape() {
        AtomicInteger opens = new AtomicInteger();
        DuplicateVideoHashingComponent failing = new DuplicateVideoHashingComponent(() -> {
            throw new IllegalStateException("no digest");
        });
        DuplicateVideoHashingResult result = failing.hash(request(DuplicateVideoMatchMode.VIDEO_AND_TEXT,
                "text", false, new DuplicateVideoHashingItem(1L, () -> {
                    opens.incrementAndGet();
                    return new ByteArrayInputStream(new byte[0]);
                })));
        assertEquals(0, opens.get());
        assertFalse(result.hasBatch());
        assertEquals(Collections.singletonList(1L), result.getFailedPresentationItemIds());

        ErrorReadSource errorSource = new ErrorReadSource();
        try {
            new DuplicateVideoHashingComponent().hash(request(DuplicateVideoMatchMode.VIDEO, "", false,
                    new DuplicateVideoHashingItem(2L, errorSource)));
            fail("Expected Error");
        } catch (AssertionError expected) {
            // Errors are intentionally not converted to per-item failure.
        }
        assertTrue(errorSource.closed);
    }

    private static byte[] key(DuplicateVideoHashingComponent component, String text) {
        return component.hash(request(DuplicateVideoMatchMode.VIDEO_AND_TEXT, text, false,
                item(1, bytes("video")))).getBatchOrNull().getItems().get(0).getMatchKey();
    }

    private static DuplicateVideoHashingRequest request(
            DuplicateVideoMatchMode mode, String text, boolean other, DuplicateVideoHashingItem... items) {
        return new DuplicateVideoHashingRequest(mode, 1, Arrays.asList(items), text, other);
    }

    private static DuplicateVideoHashingItem item(long id, byte[] bytes) {
        return new DuplicateVideoHashingItem(id, () -> new ByteArrayInputStream(bytes));
    }

    private static DuplicateVideoHashingItem item(long id, InputStream stream) {
        return new DuplicateVideoHashingItem(id, () -> stream);
    }

    private static DuplicateVideoReadableSource trackingSource(
            String name, byte[] content, List<String> events, boolean unused
    ) {
        return () -> {
            events.add("open " + name);
            return new ByteArrayInputStream(content) {
                private boolean read;
                @Override public synchronized int read(byte[] buffer, int offset, int length) {
                    int count = super.read(buffer, offset, length);
                    if (!read && count != -1) { events.add("read " + name); read = true; }
                    return count;
                }
                @Override public void close() throws IOException { events.add("close " + name); super.close(); }
            };
        };
    }

    private static List<Long> ids(DuplicateVideoBatch batch) {
        List<Long> ids = new ArrayList<>();
        for (org.cleargram.api.DuplicateVideoItem item : batch.getItems()) ids.add(item.getPresentationItemId());
        return ids;
    }

    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (Exception exception) { throw new AssertionError(exception); }
    }

    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    private static byte[] hex(String value) {
        byte[] bytes = new byte[value.length() / 2];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        return bytes;
    }
    private static void expectNull(Runnable action) {
        try { action.run(); fail("Expected NullPointerException"); }
        catch (NullPointerException expected) { }
    }
    private static void assertDifferent(byte[] first, byte[] second) {
        assertFalse(Arrays.equals(first, second));
    }

    private static final class ChunkedInputStream extends ByteArrayInputStream {
        private final int chunkSize;
        private ChunkedInputStream(byte[] data, int chunkSize) { super(data); this.chunkSize = chunkSize; }
        @Override public synchronized int read(byte[] buffer, int offset, int length) {
            return super.read(buffer, offset, Math.min(length, chunkSize));
        }
    }

    private static final class CloseFailingInputStream extends ByteArrayInputStream {
        private boolean closed;
        private CloseFailingInputStream(byte[] data) { super(data); }
        @Override public void close() throws IOException { closed = true; throw new IOException("close failure"); }
    }

    private interface StreamFactory { InputStream open() throws IOException; }

    private static final class CountingSource implements DuplicateVideoReadableSource {
        private final StreamFactory factory;
        private int opens;
        private CountingSource(StreamFactory factory) { this.factory = factory; }
        @Override public InputStream openStream() throws IOException { opens++; return factory.open(); }
    }

    private static CountingSource successfulSource(byte[] bytes) {
        return new CountingSource(() -> new ByteArrayInputStream(bytes));
    }

    private static final class ReadFailingSource implements DuplicateVideoReadableSource {
        private final boolean runtime;
        private int opens;
        private boolean closed;
        private ReadFailingSource(boolean runtime) { this.runtime = runtime; }
        @Override public InputStream openStream() {
            opens++;
            return new InputStream() {
                private boolean first = true;
                @Override public int read() throws IOException {
                    if (first) { first = false; return 'x'; }
                    if (runtime) throw new IllegalStateException("read failure");
                    throw new IOException("read failure");
                }
                @Override public void close() { closed = true; }
            };
        }
    }

    private static final class ErrorReadSource implements DuplicateVideoReadableSource {
        private boolean closed;
        @Override public InputStream openStream() {
            return new InputStream() {
                @Override public int read() { throw new AssertionError("fatal"); }
                @Override public void close() { closed = true; }
            };
        }
    }

    private static DuplicateVideoHashingComponent.DigestFactory sequenceFactory(MessageDigest... digests) {
        Deque<MessageDigest> values = new ArrayDeque<>(Arrays.asList(digests));
        return () -> {
            if (values.isEmpty()) throw new AssertionError("Unexpected digest request");
            return values.removeFirst();
        };
    }

    private static final class FixedDigest extends MessageDigest {
        private final byte[] result;
        private final RuntimeException failure;
        private FixedDigest(byte[] result) { super("fixed"); this.result = result; this.failure = null; }
        private FixedDigest(RuntimeException failure) { super("fixed"); this.result = null; this.failure = failure; }
        @Override protected void engineUpdate(byte input) { }
        @Override protected void engineUpdate(byte[] input, int offset, int length) { }
        @Override protected byte[] engineDigest() { if (failure != null) throw failure; return result; }
        @Override protected void engineReset() { }
    }
}

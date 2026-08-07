package org.cleargram.storage.telegram;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.cleargram.spi.WhiteListStoragePort;
import org.cleargram.spi.WhiteListStorageRecord;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.SQLite.SQLiteException;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.messenger.DispatchQueue;

import java.io.File;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(AndroidJUnit4.class)
public final class TelegramWhiteListStorageAdapterTest {

    @Test
    public void testFreshDatabaseLoadsEmptyImmutableSnapshot() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Probe probe = new Probe();
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            fixture.registerBlockerRelease(release);
            fixture.queue.postRunnable(() -> { entered.countDown(); await(release); });
            assertTrue(entered.await(15, TimeUnit.SECONDS));
            fixture.loadRules(probe);
            assertEquals(0, probe.loadedCount.get());
            assertEquals(0, probe.failedCount.get());
            release.countDown(); fixture.clearBlockerRelease();
            fixture.awaitSingleTerminal(probe);
            assertEquals(1, probe.loadedCount.get());
            assertEquals(0, probe.failedCount.get());
            assertNotNull(probe.records.get());
            assertTrue(probe.records.get().isEmpty());
            try {
                probe.records.get().add(new WhiteListStorageRecord("unexpected", true));
                fail("Expected immutable snapshot");
            } catch (UnsupportedOperationException expected) {
            }
            assertTrue(probe.callbackThread.get() == fixture.queue.getHandler().getLooper().getThread());
            fixture.run(() -> fixture.adapter.insertRule(new WhiteListStorageRecord("ready", true)));
            fixture.closeAdapter();
        }
    }

    @Test
    public void testInsertDeleteAndUpdatePersistAfterReady() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Probe probe = new Probe();
            fixture.loadRules(probe);
            fixture.awaitSingleTerminal(probe);
            fixture.run(() -> {
                fixture.adapter.insertRule(new WhiteListStorageRecord("A", true));
                assertTrue(fixture.adapter.updateRuleEnabled("A", false));
                fixture.adapter.insertRule(new WhiteListStorageRecord("B", true));
                assertTrue(fixture.adapter.deleteRule("B"));
            });
            fixture.closeAdapter();
            fixture.raw(raw -> { assertEquals(1, raw.executeInt("SELECT count(*) FROM white_list_rules").intValue()); assertEquals(0, raw.executeInt("SELECT enabled FROM white_list_rules WHERE canonical_pattern = 'A'").intValue()); assertEquals(0, raw.executeInt("SELECT count(*) FROM white_list_rules WHERE canonical_pattern = 'B'").intValue()); assertEquals(1, raw.executeInt("PRAGMA user_version").intValue()); });
        }
    }

    @Test
    public void testAllWritesBeforeReadyAreRejected() throws Exception {
        try (Fixture fixture = new Fixture()) {
            assertQueueThrows(fixture, () -> fixture.adapter.insertRule(new WhiteListStorageRecord("A", true)));
            assertQueueThrows(fixture, () -> fixture.adapter.deleteRule("A"));
            assertQueueThrows(fixture, () -> fixture.adapter.updateRuleEnabled("A", true));
            assertFalse(fixture.databaseFile.exists());
            Probe probe = new Probe();
            fixture.loadRules(probe);
            fixture.awaitSingleTerminal(probe);
            assertDirectThrows(() -> fixture.adapter.insertRule(new WhiteListStorageRecord("A", true)));
            fixture.closeAdapter();
        }
    }

    @Test
    public void testAllWrongThreadWritesAreRejected() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Probe probe = new Probe(); fixture.loadRules(probe); fixture.awaitSingleTerminal(probe);
            assertDirectThrows(() -> fixture.adapter.insertRule(new WhiteListStorageRecord("A", true)));
            assertDirectThrows(() -> fixture.adapter.deleteRule("A"));
            assertDirectThrows(() -> fixture.adapter.updateRuleEnabled("A", false));
            fixture.run(() -> fixture.adapter.insertRule(new WhiteListStorageRecord("A", true)));
            fixture.closeAdapter();
        }
    }

    @Test
    public void testAllWritesAfterCloseAreRejected() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Probe probe = new Probe(); fixture.loadRules(probe); fixture.awaitSingleTerminal(probe);
            fixture.closeAdapter();
            assertQueueThrows(fixture, () -> fixture.adapter.insertRule(new WhiteListStorageRecord("A", true)));
            assertQueueThrows(fixture, () -> fixture.adapter.deleteRule("A"));
            assertQueueThrows(fixture, () -> fixture.adapter.updateRuleEnabled("A", false));
            fixture.closeAdapter();
        }
    }

    @Test
    public void testMissingDeleteAndUpdateLeaveAdapterReady() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Probe probe = new Probe(); fixture.loadRules(probe); fixture.awaitSingleTerminal(probe);
            fixture.run(() -> {
                assertFalse(fixture.adapter.deleteRule("missing"));
                assertFalse(fixture.adapter.updateRuleEnabled("missing", true));
                fixture.adapter.insertRule(new WhiteListStorageRecord("A", true));
            });
            fixture.closeAdapter();
        }
    }

    @Test
    public void testCloseDuringLoadingIsRejected() throws Exception {
        try (Fixture fixture = new Fixture()) {
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            AtomicReference<Throwable> closeFailure = new AtomicReference<>();
            fixture.registerBlockerRelease(release);
            fixture.queue.postRunnable(() -> { entered.countDown(); await(release); try { fixture.adapter.close(); } catch (Throwable throwable) { closeFailure.set(throwable); } });
            assertTrue(entered.await(15, TimeUnit.SECONDS));
            Probe probe = new Probe(); fixture.loadRules(probe);
            release.countDown(); fixture.clearBlockerRelease(); fixture.awaitSingleTerminal(probe);
            assertTrue(closeFailure.get() instanceof IllegalStateException);
            assertEquals(1, probe.loadedCount.get());
            fixture.closeAdapter();
        }
    }

    @Test
    public void testCloseReadyIsIdempotentAndDoesNotRecycleQueue() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Probe probe = new Probe(); fixture.loadRules(probe); fixture.awaitSingleTerminal(probe);
            fixture.closeAdapter();
            fixture.closeAdapter();
            CountDownLatch barrier = new CountDownLatch(1);
            fixture.queue.postRunnable(barrier::countDown);
            assertTrue(barrier.await(15, TimeUnit.SECONDS));
        }
    }

    @Test
    public void testDuplicateLoadRulesIsRejectedSynchronously() throws Exception {
        try (Fixture fixture = new Fixture()) {
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            fixture.registerBlockerRelease(release);
            fixture.queue.postRunnable(() -> { entered.countDown(); await(release); });
            assertTrue(entered.await(15, TimeUnit.SECONDS));
            Probe first = new Probe();
            fixture.loadRules(first);
            assertDirectThrows(() -> fixture.adapter.loadRules(new Probe()));
            release.countDown();
            fixture.clearBlockerRelease();
            fixture.awaitSingleTerminal(first);
            assertEquals(1, first.loadedCount.get());
            assertEquals(0, first.failedCount.get());
            assertEquals(1, first.terminalCount());
            fixture.closeAdapter();
        }
    }

    @Test
    public void testCloseFromNewIsIdempotentAndCreatesNoDatabase() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.closeAdapter();
            fixture.closeAdapter();
            assertFalse(fixture.databaseFile.exists());
            assertDirectThrows(() -> fixture.adapter.loadRules(new Probe()));
            assertQueueThrows(fixture, () -> fixture.adapter.insertRule(new WhiteListStorageRecord("A", true)));
            assertQueueThrows(fixture, () -> fixture.adapter.deleteRule("A"));
            assertQueueThrows(fixture, () -> fixture.adapter.updateRuleEnabled("A", true));
            fixture.barrier();
        }
    }

    @Test
    public void testLoadFailureReportsSinglePlatformNeutralFailure() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.raw(raw -> { execute(raw, "CREATE TABLE marker (value TEXT)"); execute(raw, "INSERT INTO marker VALUES('kept')"); execute(raw, "PRAGMA user_version = 2"); });
            Probe probe = new Probe();
            fixture.loadRules(probe);
            fixture.awaitSingleTerminal(probe);
            assertEquals(0, probe.loadedCount.get());
            assertEquals(1, probe.failedCount.get());
            assertNotNull(probe.error.get());
            assertFalse(probe.error.get() instanceof SQLiteException);
            assertTrue(probe.error.get().getCause() instanceof SQLiteException);
            assertQueueThrows(fixture, () -> fixture.adapter.insertRule(new WhiteListStorageRecord("A", true)));
            fixture.closeAdapter();
            fixture.raw(raw -> { assertEquals(2, raw.executeInt("PRAGMA user_version").intValue()); assertEquals(1, raw.executeInt("SELECT count(*) FROM marker WHERE value = 'kept'").intValue()); });
        }
    }

    @Test
    public void testCloseAndLoadRaceHasSingleAtomicWinner() throws Exception {
        try (Fixture fixture = new Fixture()) {
            CountDownLatch closeReady = new CountDownLatch(1);
            CountDownLatch loadReady = new CountDownLatch(1);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch closeDone = new CountDownLatch(1);
            AtomicReference<Throwable> closeResult = new AtomicReference<>();
            AtomicReference<Throwable> loadResult = new AtomicReference<>();
            Probe probe = new Probe();
            fixture.registerBlockerRelease(start);
            fixture.queue.postRunnable(() -> {
                closeReady.countDown();
                await(start);
                try {
                    fixture.adapter.close();
                    fixture.adapterClosed.set(true);
                } catch (Throwable throwable) {
                    closeResult.set(throwable);
                } finally {
                    closeDone.countDown();
                }
            });
            Thread caller = new Thread(() -> { loadReady.countDown(); await(start); try { fixture.loadRules(probe); } catch (Throwable throwable) { loadResult.set(throwable); } }, "CleargramAdapterRaceCaller");
            caller.start();
            assertTrue(closeReady.await(15, TimeUnit.SECONDS)); assertTrue(loadReady.await(15, TimeUnit.SECONDS));
            start.countDown(); fixture.clearBlockerRelease();
            caller.join(TimeUnit.SECONDS.toMillis(15));
            assertFalse("Load caller did not terminate", caller.isAlive());
            assertTrue("Close operation did not terminate", closeDone.await(15, TimeUnit.SECONDS));
            if (closeResult.get() == null) {
                assertTrue(loadResult.get() instanceof IllegalStateException);
                assertEquals(0, probe.terminalCount());
                assertFalse(fixture.databaseFile.exists());
                assertTrue(fixture.adapterClosed.get());
            } else {
                assertTrue(closeResult.get() instanceof IllegalStateException);
                assertEquals(null, loadResult.get());
                fixture.awaitSingleTerminal(probe);
                fixture.closeAdapter();
            }
        }
    }

    @Test
    public void testCallbackExceptionDoesNotTriggerSecondCallback() throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.raw(raw -> { execute(raw, "CREATE TABLE marker (value TEXT)"); execute(raw, "INSERT INTO marker VALUES('kept')"); execute(raw, "PRAGMA user_version = 2"); });
            CountDownLatch uncaught = new CountDownLatch(1);
            AtomicReference<Throwable> uncaughtError = new AtomicReference<>();
            fixture.run(() -> Thread.currentThread().setUncaughtExceptionHandler((thread, throwable) -> { uncaughtError.set(throwable); uncaught.countDown(); }));
            AtomicInteger loaded = new AtomicInteger(); AtomicInteger failed = new AtomicInteger(); AtomicReference<Throwable> received = new AtomicReference<>();
            fixture.loadStarted.set(true);
            fixture.adapter.loadRules(new WhiteListStoragePort.LoadCallback() {
                @Override public void onLoaded(List<WhiteListStorageRecord> records) { loaded.incrementAndGet(); }
                @Override public void onFailed(Throwable error) { fixture.terminalReceived.set(true); failed.incrementAndGet(); received.set(error); assertDirectFailure(fixture); throw new CallbackFailure(); }
            });
            assertTrue(uncaught.await(15, TimeUnit.SECONDS));
            fixture.queue.join(TimeUnit.SECONDS.toMillis(15));
            assertFalse("Storage queue did not terminate", fixture.queue.isAlive());
            fixture.queueTerminated.set(true);
            assertEquals(0, loaded.get()); assertEquals(1, failed.get()); assertTrue(uncaughtError.get() instanceof CallbackFailure);
            assertTrue(received.get() instanceof RuntimeException); assertTrue(received.get().getCause() instanceof SQLiteException);
            fixture.raw(raw -> { assertEquals(2, raw.executeInt("PRAGMA user_version").intValue()); assertEquals(1, raw.executeInt("SELECT count(*) FROM marker WHERE value = 'kept'").intValue()); });
        } finally {
            cleanupCallbackExceptionFixture(fixture);
        }
    }

    private static void assertDirectFailure(Fixture fixture) {
        try { fixture.adapter.insertRule(new WhiteListStorageRecord("A", true)); fail("Expected IllegalStateException"); }
        catch (IllegalStateException expected) { }
    }

    private static final class CallbackFailure extends RuntimeException { }

    private static void cleanupCallbackExceptionFixture(Fixture fixture) throws Exception {
        if (!fixture.queueTerminated.get()) {
            fixture.queue.join(TimeUnit.SECONDS.toMillis(15));
            if (!fixture.queue.isAlive()) {
                fixture.queueTerminated.set(true);
            }
        }
        if (fixture.queueTerminated.get()) {
            fixture.closeAfterQueueTermination();
        } else {
            fixture.close();
        }
    }

    @Test
    public void testInitialLoadMapsRecordsAndPreservesOrder() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.raw(raw -> { createCompatibleTable(raw); execute(raw, "INSERT INTO white_list_rules VALUES('first', 1, 1)"); execute(raw, "INSERT INTO white_list_rules VALUES('second', 0, 2)"); execute(raw, "INSERT INTO white_list_rules VALUES('third', 1, 3)"); });
            Probe probe = new Probe(); fixture.loadRules(probe); fixture.awaitSingleTerminal(probe);
            assertEquals(1, probe.loadedCount.get());
            assertEquals(0, probe.failedCount.get());
            assertEquals(1, probe.terminalCount());
            assertTrue(probe.callbackThread.get() == fixture.queue.getHandler().getLooper().getThread());
            List<WhiteListStorageRecord> records = probe.records.get();
            assertNotNull(records);
            assertEquals(3, records.size());
            assertEquals("first", records.get(0).getCanonicalPattern()); assertTrue(records.get(0).isEnabled());
            assertEquals("second", records.get(1).getCanonicalPattern()); assertFalse(records.get(1).isEnabled());
            assertEquals("third", records.get(2).getCanonicalPattern()); assertTrue(records.get(2).isEnabled());
            try {
                records.add(new WhiteListStorageRecord("unexpected", true));
                fail("Expected immutable snapshot");
            } catch (UnsupportedOperationException expected) {
            }
            fixture.closeAdapter();
        }
    }

    @Test
    public void testMappingFailureIsAllOrNothing() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.raw(raw -> { createCompatibleTable(raw); execute(raw, "INSERT INTO white_list_rules VALUES('valid', 1, 1)"); execute(raw, "INSERT INTO white_list_rules VALUES('', 1, 2)"); });
            Probe probe = new Probe(); fixture.loadRules(probe); fixture.awaitSingleTerminal(probe);
            assertEquals(0, probe.loadedCount.get()); assertEquals(1, probe.failedCount.get()); assertEquals(1, probe.terminalCount());
            assertEquals(null, probe.records.get());
            assertTrue(probe.callbackThread.get() == fixture.queue.getHandler().getLooper().getThread());
            assertTrue(probe.error.get() instanceof RuntimeException);
            assertFalse(probe.error.get() instanceof SQLiteException);
            assertTrue(probe.error.get().getCause() instanceof IllegalArgumentException);
            assertQueueThrows(fixture, () -> fixture.adapter.insertRule(new WhiteListStorageRecord("A", true)));
            assertQueueThrows(fixture, () -> fixture.adapter.deleteRule("A"));
            assertQueueThrows(fixture, () -> fixture.adapter.updateRuleEnabled("A", true));
            fixture.closeAdapter();
        }
    }

    @Test
    public void testWriteFailureBecomesStorageFailureAndFailsAdapter() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Probe probe = new Probe(); fixture.loadRules(probe); fixture.awaitSingleTerminal(probe);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            fixture.run(() -> { fixture.adapter.insertRule(new WhiteListStorageRecord("A", true)); try { fixture.adapter.insertRule(new WhiteListStorageRecord("A", true)); } catch (Throwable throwable) { failure.set(throwable); } });
            assertNotNull(failure.get()); assertFalse(failure.get() instanceof SQLiteException); assertTrue(failure.get().getCause() instanceof SQLiteException);
            assertQueueThrows(fixture, () -> fixture.adapter.insertRule(new WhiteListStorageRecord("B", true)));
            assertQueueThrows(fixture, () -> fixture.adapter.deleteRule("A"));
            assertQueueThrows(fixture, () -> fixture.adapter.updateRuleEnabled("A", false));
            fixture.closeAdapter();
            fixture.raw(raw -> assertEquals(1, raw.executeInt("SELECT count(*) FROM white_list_rules WHERE canonical_pattern = 'A'").intValue()));
        }
    }

    private static void createCompatibleTable(SQLiteDatabase database) throws SQLiteException {
        execute(database, "CREATE TABLE white_list_rules (canonical_pattern TEXT NOT NULL COLLATE BINARY PRIMARY KEY, enabled INTEGER NOT NULL, order_key INTEGER NOT NULL UNIQUE)");
        execute(database, "PRAGMA user_version = 1");
    }

    private interface Operation { void run() throws Exception; }

    private static void assertDirectThrows(Operation operation) throws Exception {
        try { operation.run(); fail("Expected IllegalStateException"); }
        catch (IllegalStateException expected) { }
    }

    private static void assertQueueThrows(Fixture fixture, Operation operation) throws Exception {
        try { fixture.run(operation); fail("Expected IllegalStateException"); }
        catch (IllegalStateException expected) { }
    }

    private static void await(CountDownLatch latch) {
        try { if (!latch.await(15, TimeUnit.SECONDS)) throw new AssertionError("Timed out"); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new AssertionError(exception); }
    }

    private static void execute(SQLiteDatabase database, String sql) throws SQLiteException {
        SQLitePreparedStatement statement = null;
        try { statement = database.executeFast(sql); statement.stepThis(); }
        finally { if (statement != null) statement.dispose(); }
    }

    private static final class Probe implements WhiteListStoragePort.LoadCallback {
        final CountDownLatch terminal = new CountDownLatch(1);
        final AtomicInteger loadedCount = new AtomicInteger();
        final AtomicInteger failedCount = new AtomicInteger();
        final AtomicReference<List<WhiteListStorageRecord>> records = new AtomicReference<>();
        final AtomicReference<Throwable> error = new AtomicReference<>();
        final AtomicReference<Thread> callbackThread = new AtomicReference<>();
        @Override public void onLoaded(List<WhiteListStorageRecord> records) { callbackThread.set(Thread.currentThread()); this.records.set(records); loadedCount.incrementAndGet(); terminal.countDown(); }
        @Override public void onFailed(Throwable error) { callbackThread.set(Thread.currentThread()); this.error.set(error); failedCount.incrementAndGet(); terminal.countDown(); }
        void await() throws InterruptedException { assertTrue("Timed out", terminal.await(15, TimeUnit.SECONDS)); }
        int terminalCount() { return loadedCount.get() + failedCount.get(); }
    }

    private static final class Fixture implements AutoCloseable {
        final File directory;
        final File databaseFile;
        final DispatchQueue queue;
        final TelegramWhiteListStorageAdapter adapter;
        final AtomicBoolean recycled = new AtomicBoolean();
        final AtomicBoolean loadStarted = new AtomicBoolean();
        final AtomicBoolean terminalReceived = new AtomicBoolean();
        final AtomicBoolean adapterClosed = new AtomicBoolean();
        final AtomicBoolean queueTerminated = new AtomicBoolean();
        final AtomicReference<CountDownLatch> blockerRelease = new AtomicReference<>();
        final AtomicReference<Probe> activeProbe = new AtomicReference<>();
        Fixture() {
            directory = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir(), "cleargram-adapter-test-" + UUID.randomUUID());
            assertTrue(directory.mkdirs());
            databaseFile = new File(directory, "noisegram.db");
            queue = new DispatchQueue("CleargramAdapterTest-" + UUID.randomUUID());
            adapter = new TelegramWhiteListStorageAdapter(databaseFile, queue);
        }
        void run(Operation operation) throws Exception {
            CountDownLatch done = new CountDownLatch(1); AtomicReference<Throwable> failure = new AtomicReference<>();
            queue.postRunnable(() -> { try { operation.run(); } catch (Throwable throwable) { failure.set(throwable); } finally { done.countDown(); } });
            assertTrue("Timed out", done.await(15, TimeUnit.SECONDS));
            if (failure.get() instanceof Exception) throw (Exception) failure.get();
            if (failure.get() instanceof Error) throw (Error) failure.get();
        }
        void loadRules(Probe probe) {
            adapter.loadRules(new WhiteListStoragePort.LoadCallback() {
                @Override public void onLoaded(List<WhiteListStorageRecord> records) { terminalReceived.set(true); probe.onLoaded(records); }
                @Override public void onFailed(Throwable error) { terminalReceived.set(true); probe.onFailed(error); }
            });
            loadStarted.set(true);
            activeProbe.set(probe);
        }
        void registerBlockerRelease(CountDownLatch release) { blockerRelease.set(release); }
        void clearBlockerRelease() { blockerRelease.set(null); }
        void closeAdapter() throws Exception {
            run(() -> adapter.close());
            adapterClosed.set(true);
        }
        @Override
        public void close() throws Exception {
            if (queueTerminated.get()) { closeAfterQueueTermination(); return; }
            CountDownLatch release = blockerRelease.getAndSet(null);
            if (release != null) { release.countDown(); }
            if (!adapterClosed.get()) {
                if (loadStarted.get() && !terminalReceived.get()) {
                    Probe probe = activeProbe.get();
                    if (probe == null) throw new IllegalStateException("Fixture load has no callback probe");
                    probe.await();
                }
                closeAdapter();
            }
            if (recycled.compareAndSet(false, true)) { barrier(); queue.recycle(); }
            delete(databaseFile); delete(new File(databaseFile + "-journal")); delete(new File(databaseFile + "-wal")); delete(new File(databaseFile + "-shm")); delete(directory);
        }
        void closeAfterQueueTermination() {
            delete(databaseFile); delete(new File(databaseFile + "-journal")); delete(new File(databaseFile + "-wal")); delete(new File(databaseFile + "-shm")); delete(directory);
        }
        void barrier() throws Exception { run(() -> { }); }
        void awaitSingleTerminal(Probe probe) throws Exception {
            probe.await();
            barrier();
            assertEquals(1, probe.terminalCount());
        }
        void raw(RawOperation operation) throws Exception {
            SQLiteDatabase raw = new SQLiteDatabase(databaseFile.getAbsolutePath());
            try { operation.run(raw); } finally { raw.close(); }
        }
        private static void delete(File file) { if (file.exists() && !file.delete()) file.deleteOnExit(); }
    }

    private interface RawOperation { void run(SQLiteDatabase database) throws Exception; }
}

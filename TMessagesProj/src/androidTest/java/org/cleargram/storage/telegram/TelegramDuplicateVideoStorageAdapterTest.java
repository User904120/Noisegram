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
import org.cleargram.spi.DuplicateVideoStorageClassification;
import org.cleargram.spi.DuplicateVideoStoragePort;
import org.cleargram.spi.DuplicateVideoStorageSettings;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.messenger.DispatchQueue;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public final class TelegramDuplicateVideoStorageAdapterTest {

    @Test
    public void constructorAndLoadValidateNulls() throws Exception {
        try (Fixture fixture = new Fixture()) {
            assertThrows(NullPointerException.class,
                    () -> new TelegramDuplicateVideoStorageAdapter(null, fixture.queue));
            assertThrows(NullPointerException.class,
                    () -> new TelegramDuplicateVideoStorageAdapter(fixture.whiteAdapter, null));
            assertThrows(NullPointerException.class, () -> fixture.adapter.load(null));
        }
    }

    @Test
    public void loadReturnsRawSettingsOnceOnOwningQueueAndCallbackFailureKeepsReady() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.openDatabase();
            fixture.run(() -> {
                fixture.database().updateDuplicateVideoEnabled(true);
                fixture.database().updateDuplicateVideoMatchMode(2);
            });
            Probe probe = new Probe(true);
            fixture.adapter.load(probe);
            probe.await();
            fixture.drainQueue();
            assertEquals(1, probe.loaded.get());
            assertEquals(0, probe.failed.get());
            assertEquals(1, probe.settings.get().getEnabled());
            assertEquals(2, probe.settings.get().getMatchMode());
            assertTrue(probe.callbackThread.get() == fixture.queue.getHandler().getLooper().getThread());
            fixture.run(() -> fixture.adapter.setEnabled(false));
            assertEquals(0, fixture.readEnabled());
        }
    }

    @Test
    public void operationsRejectBeforeReadyAndWrongThreadWithoutRedispatch() throws Exception {
        try (Fixture fixture = new Fixture()) {
            assertThrows(IllegalStateException.class, () -> fixture.adapter.setEnabled(true));
            fixture.openDatabase();
            Probe probe = new Probe(false);
            fixture.adapter.load(probe);
            probe.await();
            assertThrows(IllegalStateException.class, () -> fixture.adapter.setMatchMode(2));
            assertThrows(IllegalStateException.class,
                    () -> fixture.adapter.classifyOrdered(1, 1, Arrays.asList(key((byte) 1))));
        }
    }

    @Test
    public void orderedClassificationCopiesKeysAndClearHistoryUsesReadyAdapter() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.openAndLoad();
            byte[] first = key((byte) 7);
            AtomicReference<List<DuplicateVideoStorageClassification>> result = new AtomicReference<>();
            fixture.run(() -> result.set(fixture.adapter.classifyOrdered(1, 1,
                    Arrays.asList(first, Arrays.copyOf(first, first.length)))));
            first[0] = 9;
            assertEquals(Arrays.asList(DuplicateVideoStorageClassification.FIRST_SEEN,
                    DuplicateVideoStorageClassification.DUPLICATE), result.get());
            fixture.run(fixture.adapter::clearHistory);
            fixture.run(() -> result.set(fixture.adapter.classifyOrdered(1, 1, Arrays.asList(key((byte) 7)))));
            assertEquals(DuplicateVideoStorageClassification.FIRST_SEEN, result.get().get(0));
        }
    }

    @Test
    public void settingsOperationsMapOnlyTheirOwnRawSettingAndRejectInvalidModes() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.openAndLoad();
            fixture.run(() -> fixture.adapter.setEnabled(true));
            assertEquals(1, fixture.readEnabled());
            assertEquals(1, fixture.readMode());
            fixture.run(() -> fixture.adapter.setMatchMode(2));
            assertEquals(1, fixture.readEnabled());
            assertEquals(2, fixture.readMode());
            fixture.run(() -> assertThrows(IllegalArgumentException.class, () -> fixture.adapter.setMatchMode(0)));
            fixture.run(() -> assertThrows(IllegalArgumentException.class, () -> fixture.adapter.setMatchMode(-1)));
            fixture.run(() -> assertThrows(IllegalArgumentException.class, () -> fixture.adapter.setMatchMode(3)));
            assertEquals(2, fixture.readMode());
        }
    }

    @Test
    public void classificationDoesNotUseDurableEnabledSettingAsPolicyGate() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.openAndLoad();
            AtomicReference<List<DuplicateVideoStorageClassification>> result = new AtomicReference<>();
            fixture.run(() -> result.set(fixture.adapter.classifyOrdered(1, 1, Arrays.asList(key((byte) 3), key((byte) 3)))));
            assertEquals(Arrays.asList(DuplicateVideoStorageClassification.FIRST_SEEN,
                    DuplicateVideoStorageClassification.DUPLICATE), result.get());
            fixture.run(() -> fixture.adapter.setEnabled(true));
            fixture.run(() -> result.set(fixture.adapter.classifyOrdered(1, 1, Arrays.asList(key((byte) 4), key((byte) 4)))));
            assertEquals(Arrays.asList(DuplicateVideoStorageClassification.FIRST_SEEN,
                    DuplicateVideoStorageClassification.DUPLICATE), result.get());
        }
    }

    @Test
    public void invalidSettingsCauseSingleFailureCallback() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.prepareRaw("DELETE FROM settings WHERE key = 'duplicate_video_match_mode'");
            fixture.openDatabase();
            Probe probe = new Probe(false);
            fixture.adapter.load(probe);
            probe.awaitTerminal();
            fixture.drainQueue();
            assertEquals(0, probe.loaded.get());
            assertEquals(1, probe.failed.get());
            assertNotNull(probe.failure.get());
            fixture.run(() -> assertThrows(IllegalStateException.class, () -> fixture.adapter.clearHistory()));
        }
    }

    @Test
    public void rawUnknownIntegerSettingsLoadSuccessfullyWithoutDomainDefaulting() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.prepareRaw("UPDATE settings SET value = '7' WHERE key = 'duplicate_video_enabled'",
                    "UPDATE settings SET value = '9' WHERE key = 'duplicate_video_match_mode'");
            fixture.openDatabase();
            Probe probe = new Probe(false);
            fixture.adapter.load(probe);
            probe.await();
            fixture.drainQueue();
            assertEquals(1, probe.loaded.get());
            assertEquals(0, probe.failed.get());
            assertEquals(7, probe.settings.get().getEnabled());
            assertEquals(9, probe.settings.get().getMatchMode());
            assertTrue(probe.callbackThread.get() == fixture.queue.getHandler().getLooper().getThread());
            fixture.run(() -> fixture.adapter.setEnabled(false));
        }
    }

    @Test
    public void missingAndNonIntegerSettingsFailLoadWithoutRetry() throws Exception {
        assertLoadFailure("DELETE FROM settings WHERE key = 'duplicate_video_enabled'");
        assertLoadFailure("DELETE FROM settings WHERE key = 'duplicate_video_match_mode'");
        assertLoadFailure("UPDATE settings SET value = 'not-an-int' WHERE key = 'duplicate_video_enabled'");
        assertLoadFailure("UPDATE settings SET value = 'not-an-int' WHERE key = 'duplicate_video_match_mode'");
    }

    @Test
    public void schemaLoadFailureHasOneTerminalFailureAndNoRetry() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.prepareRaw("PRAGMA user_version = 5");
            fixture.openDatabaseExpectingFailure();
            Probe probe = new Probe(true);
            fixture.adapter.load(probe);
            probe.awaitTerminal();
            fixture.drainQueue();
            assertEquals(0, probe.loaded.get());
            assertEquals(1, probe.failed.get());
            assertNotNull(probe.failure.get());
            assertTrue(probe.callbackThread.get() == fixture.queue.getHandler().getLooper().getThread());
            fixture.run(() -> assertThrows(IllegalStateException.class, () -> fixture.adapter.clearHistory()));
            assertThrows(IllegalStateException.class, () -> fixture.adapter.load(new Probe(false)));
        }
    }

    @Test
    public void setEnabledFailureKeepsAdapterReadyAfterFixtureRecovery() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.openAndLoad();
            fixture.mutateRaw("DELETE FROM settings WHERE key = 'duplicate_video_enabled'");
            fixture.run(() -> assertThrows(RuntimeException.class, () -> fixture.adapter.setEnabled(true)));
            fixture.mutateRaw("INSERT INTO settings VALUES('duplicate_video_enabled', '0')");
            fixture.run(() -> fixture.adapter.setEnabled(true));
            assertEquals(1, fixture.readEnabled());
        }
    }

    @Test
    public void setMatchModeFailureKeepsAdapterReadyAfterFixtureRecovery() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.openAndLoad();
            fixture.mutateRaw("DELETE FROM settings WHERE key = 'duplicate_video_match_mode'");
            fixture.run(() -> assertThrows(RuntimeException.class, () -> fixture.adapter.setMatchMode(2)));
            fixture.mutateRaw("INSERT INTO settings VALUES('duplicate_video_match_mode', '1')");
            fixture.run(() -> fixture.adapter.setMatchMode(2));
            assertEquals(2, fixture.readMode());
        }
    }

    @Test
    public void clearHistoryFailureKeepsAdapterReadyAfterFixtureRecovery() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.openAndLoad();
            fixture.mutateRaw("DROP TABLE duplicate_video_history");
            fixture.run(() -> assertThrows(RuntimeException.class, fixture.adapter::clearHistory));
            fixture.mutateRaw(Fixture.CREATE_HISTORY_TABLE);
            fixture.run(fixture.adapter::clearHistory);
        }
    }

    @Test
    public void classificationFailureMakesAdapterFailedAndRecoveryDoesNotReloadIt() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.openAndLoad();
            fixture.mutateRaw("DROP TABLE duplicate_video_history");
            fixture.run(() -> assertThrows(RuntimeException.class,
                    () -> fixture.adapter.classifyOrdered(1, 1, Arrays.asList(key((byte) 8)))));
            fixture.mutateRaw(Fixture.CREATE_HISTORY_TABLE);
            fixture.run(() -> {
                assertThrows(IllegalStateException.class, () -> fixture.adapter.setEnabled(true));
                assertThrows(IllegalStateException.class, () -> fixture.adapter.setMatchMode(2));
                assertThrows(IllegalStateException.class, () -> fixture.adapter.clearHistory());
                assertThrows(IllegalStateException.class,
                        () -> fixture.adapter.classifyOrdered(1, 1, Arrays.asList(key((byte) 8))));
            });
        }
    }

    private void assertLoadFailure(String rawSql) throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.prepareRaw(rawSql);
            fixture.openDatabase();
            Probe probe = new Probe(false);
            fixture.adapter.load(probe);
            probe.awaitTerminal();
            fixture.drainQueue();
            assertEquals(0, probe.loaded.get());
            assertEquals(1, probe.failed.get());
            assertNotNull(probe.failure.get());
            assertTrue(probe.callbackThread.get() == fixture.queue.getHandler().getLooper().getThread());
            fixture.run(() -> assertThrows(IllegalStateException.class, () -> fixture.adapter.setEnabled(true)));
            assertThrows(IllegalStateException.class, () -> fixture.adapter.load(new Probe(false)));
        }
    }

    private static byte[] key(byte value) {
        byte[] key = new byte[32];
        Arrays.fill(key, value);
        return key;
    }

    private static void assertThrows(Class<? extends Throwable> type, ThrowingOperation operation) throws Exception {
        try { operation.run(); fail("Expected " + type.getSimpleName()); }
        catch (Throwable actual) { assertTrue("Expected " + type.getSimpleName() + " but was " + actual, type.isInstance(actual)); }
    }

    private static final class Probe implements DuplicateVideoStoragePort.LoadCallback {
        final CountDownLatch terminal = new CountDownLatch(1);
        final AtomicInteger loaded = new AtomicInteger();
        final AtomicInteger failed = new AtomicInteger();
        final AtomicReference<DuplicateVideoStorageSettings> settings = new AtomicReference<>();
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final AtomicReference<Thread> callbackThread = new AtomicReference<>();
        final boolean throwFromLoaded;
        Probe(boolean throwFromLoaded) { this.throwFromLoaded = throwFromLoaded; }
        @Override public void onLoaded(DuplicateVideoStorageSettings value) {
            settings.set(value); callbackThread.set(Thread.currentThread()); loaded.incrementAndGet(); terminal.countDown();
            if (throwFromLoaded) throw new IllegalStateException("callback");
        }
        @Override public void onFailed(Throwable error) { failure.set(error); callbackThread.set(Thread.currentThread()); failed.incrementAndGet(); terminal.countDown(); }
        void await() throws Exception { awaitTerminal(); assertEquals(null, failure.get()); }
        void awaitTerminal() throws Exception { assertTrue("Timed out waiting for terminal callback", terminal.await(15, TimeUnit.SECONDS)); }
    }

    private static final class Fixture implements AutoCloseable {
        static final String CREATE_HISTORY_TABLE = "CREATE TABLE duplicate_video_history ("
                + "id INTEGER PRIMARY KEY,match_mode INTEGER NOT NULL CHECK (match_mode IN (1, 2)),"
                + "key_version INTEGER NOT NULL CHECK (key_version > 0),match_key BLOB NOT NULL,"
                + "first_seen INTEGER NOT NULL CHECK (first_seen >= 0),"
                + "last_seen INTEGER NOT NULL CHECK (last_seen >= first_seen),"
                + "UNIQUE (match_mode, key_version, match_key))";
        final File directory;
        final File databaseFile;
        final DispatchQueue queue;
        final TelegramWhiteListStorageAdapter whiteAdapter;
        final TelegramDuplicateVideoStorageAdapter adapter;

        Fixture() {
            directory = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir(), "cleargram-duplicate-adapter-" + UUID.randomUUID());
            assertTrue(directory.mkdirs());
            databaseFile = new File(directory, "noisegram.db");
            queue = new DispatchQueue("CleargramDuplicateVideoAdapterTest-" + UUID.randomUUID());
            whiteAdapter = new TelegramWhiteListStorageAdapter(databaseFile, queue);
            adapter = new TelegramDuplicateVideoStorageAdapter(whiteAdapter, queue);
        }
        CleargramDatabase database() { return whiteAdapter.getDatabase(); }
        void openDatabase() throws Exception {
            CountDownLatch done = new CountDownLatch(1); AtomicReference<Throwable> failure = new AtomicReference<>();
            whiteAdapter.loadRules(new org.cleargram.spi.WhiteListStoragePort.LoadCallback() {
                @Override public void onLoaded(List<org.cleargram.spi.WhiteListStorageRecord> ignored) { done.countDown(); }
                @Override public void onFailed(Throwable error) { failure.set(error); done.countDown(); }
            });
            assertTrue("Timed out opening database", done.await(15, TimeUnit.SECONDS));
            if (failure.get() != null) throw new AssertionError(failure.get());
        }
        void openDatabaseExpectingFailure() throws Exception {
            CountDownLatch done = new CountDownLatch(1); AtomicReference<Throwable> failure = new AtomicReference<>();
            whiteAdapter.loadRules(new org.cleargram.spi.WhiteListStoragePort.LoadCallback() {
                @Override public void onLoaded(List<org.cleargram.spi.WhiteListStorageRecord> ignored) { done.countDown(); }
                @Override public void onFailed(Throwable error) { failure.set(error); done.countDown(); }
            });
            assertTrue("Timed out opening failing database", done.await(15, TimeUnit.SECONDS));
            assertNotNull(failure.get());
        }
        void prepareRaw(String... sql) throws Exception {
            CleargramDatabase setup = new CleargramDatabase(databaseFile, queue);
            run(() -> { setup.open(); setup.close(); });
            SQLiteDatabase raw = new SQLiteDatabase(databaseFile.getAbsolutePath());
            try {
                for (String statement : sql) {
                    SQLitePreparedStatement prepared = raw.executeFast(statement);
                    try { prepared.stepThis(); } finally { prepared.dispose(); }
                }
            } finally { raw.close(); }
        }
        void mutateRaw(String... sql) throws Exception {
            SQLiteDatabase raw = new SQLiteDatabase(databaseFile.getAbsolutePath());
            try {
                for (String statement : sql) {
                    SQLitePreparedStatement prepared = raw.executeFast(statement);
                    try { prepared.stepThis(); } finally { prepared.dispose(); }
                }
            } finally { raw.close(); }
        }
        void openAndLoad() throws Exception { openDatabase(); Probe probe = new Probe(false); adapter.load(probe); probe.await(); }
        int readEnabled() throws Exception { AtomicReference<Integer> result = new AtomicReference<>(); run(() -> result.set(database().loadDuplicateVideoSettings().getEnabled())); return result.get(); }
        int readMode() throws Exception { AtomicReference<Integer> result = new AtomicReference<>(); run(() -> result.set(database().loadDuplicateVideoSettings().getMatchMode())); return result.get(); }
        void drainQueue() throws Exception { run(() -> { }); }
        void run(ThrowingOperation operation) throws Exception {
            CountDownLatch done = new CountDownLatch(1); AtomicReference<Throwable> failure = new AtomicReference<>();
            queue.postRunnable(() -> { try { operation.run(); } catch (Throwable error) { failure.set(error); } finally { done.countDown(); } });
            assertTrue("Timed out waiting for queue", done.await(15, TimeUnit.SECONDS));
            if (failure.get() instanceof Exception) throw (Exception) failure.get();
            if (failure.get() instanceof Error) throw (Error) failure.get();
        }
        @Override public void close() throws Exception {
            run(whiteAdapter::close); queue.recycle(); delete(databaseFile); delete(new File(databaseFile + "-journal")); delete(directory);
        }
        private static void delete(File file) { if (file.exists() && !file.delete()) file.deleteOnExit(); }
    }
    private interface ThrowingOperation { void run() throws Exception; }
}

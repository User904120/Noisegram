package org.cleargram.storage.telegram;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.cleargram.api.NoiseAction;
import org.cleargram.spi.BlackListStoragePort;
import org.cleargram.spi.BlackListStorageState;
import org.telegram.messenger.DispatchQueue;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public final class TelegramBlackListStorageAdapterTest {

    private static final String[] DEFAULT_BLACK_LIST_PATTERNS = {
            "#реклама", "рекламодатель", "erid:", "ставки", "каппер"
    };

    @Test
    public void freshDatabaseInitialStateContainsDefaultBlackListRules() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.openDatabase();

            Probe probe = new Probe();
            fixture.blackAdapter.loadState(probe);
            probe.await();

            assertEquals(NoiseAction.COLLAPSE, probe.state.get().getAction());
            assertEquals(DEFAULT_BLACK_LIST_PATTERNS.length, probe.state.get().getRecords().size());
            for (int index = 0; index < DEFAULT_BLACK_LIST_PATTERNS.length; index++) {
                assertEquals(DEFAULT_BLACK_LIST_PATTERNS[index],
                        probe.state.get().getRecords().get(index).getCanonicalPattern());
                assertEquals(NoiseAction.COLLAPSE,
                        probe.state.get().getRecords().get(index).getAction());
                assertTrue(probe.state.get().getRecords().get(index).isEnabled());
            }
            assertTrue(probe.callbackThread.get() == fixture.queue.getHandler().getLooper().getThread());
        }
    }

    @Test
    public void loadStateReturnsActionAndOrderedRecordsOnOwningQueue() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.openDatabase();
            fixture.run(() -> {
                fixture.whiteAdapter.getDatabase().insertBlackListRow("first", NoiseAction.HIDE, true);
                fixture.whiteAdapter.getDatabase().insertBlackListRow("second", NoiseAction.COLLAPSE, false);
            });

            Probe probe = new Probe();
            fixture.blackAdapter.loadState(probe);
            probe.await();

            assertEquals(NoiseAction.COLLAPSE, probe.state.get().getAction());
            assertEquals("first", probe.state.get().getRecords().get(5).getCanonicalPattern());
            assertEquals("second", probe.state.get().getRecords().get(6).getCanonicalPattern());
            assertTrue(probe.callbackThread.get() == fixture.queue.getHandler().getLooper().getThread());

            fixture.run(() -> fixture.blackAdapter.updateAction(NoiseAction.HIDE));
            assertEquals(NoiseAction.HIDE, fixture.readActionOnQueue());
        }
    }

    @Test(expected = IllegalStateException.class)
    public void updateActionRejectsWrongThreadBeforeReady() {
        Fixture fixture = new Fixture();
        try {
            fixture.blackAdapter.updateAction(NoiseAction.HIDE);
        } finally {
            fixture.closeQuietly();
        }
    }

    private static final class Probe implements BlackListStoragePort.LoadCallback {
        final CountDownLatch terminal = new CountDownLatch(1);
        final AtomicReference<BlackListStorageState> state = new AtomicReference<>();
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final AtomicReference<Thread> callbackThread = new AtomicReference<>();

        @Override public void onLoaded(BlackListStorageState state) {
            this.state.set(state);
            callbackThread.set(Thread.currentThread());
            terminal.countDown();
        }

        @Override public void onFailed(Throwable error) {
            failure.set(error);
            callbackThread.set(Thread.currentThread());
            terminal.countDown();
        }

        void await() throws Exception {
            assertTrue("Timed out waiting for load", terminal.await(15, TimeUnit.SECONDS));
            if (failure.get() != null) throw new AssertionError(failure.get());
        }
    }

    private static final class Fixture implements AutoCloseable {
        final File directory;
        final File databaseFile;
        final DispatchQueue queue;
        final TelegramWhiteListStorageAdapter whiteAdapter;
        final TelegramBlackListStorageAdapter blackAdapter;

        Fixture() {
            directory = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir(), "cleargram-black-adapter-" + UUID.randomUUID());
            assertTrue(directory.mkdirs());
            databaseFile = new File(directory, "noisegram.db");
            queue = new DispatchQueue("CleargramBlackListAdapterTest-" + UUID.randomUUID());
            whiteAdapter = new TelegramWhiteListStorageAdapter(databaseFile, queue);
            blackAdapter = new TelegramBlackListStorageAdapter(whiteAdapter, queue);
        }

        void openDatabase() throws Exception {
            CountDownLatch terminal = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            whiteAdapter.loadRules(new org.cleargram.spi.WhiteListStoragePort.LoadCallback() {
                @Override public void onLoaded(java.util.List<org.cleargram.spi.WhiteListStorageRecord> records) { terminal.countDown(); }
                @Override public void onFailed(Throwable error) { failure.set(error); terminal.countDown(); }
            });
            assertTrue("Timed out opening database", terminal.await(15, TimeUnit.SECONDS));
            if (failure.get() != null) throw new AssertionError(failure.get());
        }

        NoiseAction readActionOnQueue() throws Exception {
            AtomicReference<NoiseAction> action = new AtomicReference<>();
            run(() -> action.set(whiteAdapter.getDatabase().loadBlackListAction()));
            return action.get();
        }

        void run(Operation operation) throws Exception {
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            queue.postRunnable(() -> {
                try { operation.run(); } catch (Throwable error) { failure.set(error); } finally { done.countDown(); }
            });
            assertTrue("Timed out waiting for queue", done.await(15, TimeUnit.SECONDS));
            if (failure.get() instanceof Exception) throw (Exception) failure.get();
            if (failure.get() instanceof Error) throw (Error) failure.get();
        }

        @Override public void close() throws Exception {
            run(whiteAdapter::close);
            queue.recycle();
            delete(databaseFile);
            delete(new File(databaseFile + "-journal"));
            delete(directory);
        }

        void closeQuietly() {
            queue.recycle();
            delete(databaseFile);
            delete(directory);
        }

        private static void delete(File file) { if (file.exists() && !file.delete()) file.deleteOnExit(); }
    }

    private interface Operation { void run() throws Exception; }
}

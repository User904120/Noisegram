package org.cleargram.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.os.Looper;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.cleargram.api.NoiseBootstrap;
import org.cleargram.api.WhiteListRuleSnapshot;
import org.cleargram.storage.telegram.TelegramWhiteListStorageAdapter;
import org.telegram.messenger.DispatchQueue;

import java.io.File;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public final class TelegramWhiteListManagementGatewayTest {

    private static final long TIMEOUT_SECONDS = 15;

    private Fixture fixture;

    @Before
    public void setUp() throws Exception {
        cleanupPlatformBootstrap();
        resetNoiseBootstrap();
        fixture = new Fixture(createDirectory("cleargram_management_gateway_"));
    }

    @After
    public void tearDown() throws Exception {
        try {
            fixture.close();
        } finally {
            resetNoiseBootstrap();
        }
    }

    @Test
    public void testAccessorBeforeInitializationIsRejected() {
        try {
            TelegramNoiseBootstrap.getWhiteListManagementGateway();
            fail("Expected accessor to reject before initialization");
        } catch (IllegalStateException expected) {
            // Expected fail-fast lifecycle boundary.
        }
    }

    @Test
    public void testSuccessfulBootstrapPublishesSingleGateway() throws Exception {
        fixture.initializeReady();

        TelegramWhiteListManagementGateway first =
                TelegramNoiseBootstrap.getWhiteListManagementGateway();
        TelegramWhiteListManagementGateway second =
                TelegramNoiseBootstrap.getWhiteListManagementGateway();

        assertNotNull(first);
        assertSame(first, second);
        assertSame(first, fixture.getPublishedGateway());

        File secondDirectory = createDirectory("cleargram_management_gateway_second_");
        try {
            TelegramNoiseBootstrap.initialize(secondDirectory);
            assertSame(first, TelegramNoiseBootstrap.getWhiteListManagementGateway());
            assertFalse(new File(secondDirectory, "noisegram.db").exists());
        } finally {
            deleteStorageFiles(secondDirectory);
        }
    }

    @Test
    public void testFreshDatabaseReturnsEmptyImmutableRulesOnUiThread() throws Exception {
        fixture.initializeReady();

        Probe<List<WhiteListRuleSnapshot>> probe = new Probe<>();
        fixture.gateway().getRules(probe);

        List<WhiteListRuleSnapshot> rules = probe.awaitSuccess();
        assertTrue(probe.callbackOnUiThread.get());
        assertEquals(0, rules.size());
        try {
            rules.add(new WhiteListRuleSnapshot("unexpected", true));
            fail("Expected immutable result");
        } catch (UnsupportedOperationException expected) {
            // Expected defensive immutable copy.
        }
    }

    @Test
    public void testAddUpdateRemoveAndRepeatedReadPersistThroughGateway() throws Exception {
        fixture.initializeReady();
        TelegramWhiteListManagementGateway gateway = fixture.gateway();

        Probe<WhiteListRuleSnapshot> add = new Probe<>();
        gateway.addRule("important", true, add);
        WhiteListRuleSnapshot created = add.awaitSuccess();
        assertTrue(add.callbackOnUiThread.get());
        assertEquals("important", created.getCanonicalPattern());
        assertTrue(created.isEnabled());

        Probe<List<WhiteListRuleSnapshot>> firstRead = new Probe<>();
        gateway.getRules(firstRead);
        List<WhiteListRuleSnapshot> firstRules = firstRead.awaitSuccess();
        assertEquals(1, firstRules.size());
        assertEquals("important", firstRules.get(0).getCanonicalPattern());
        assertTrue(firstRules.get(0).isEnabled());

        Probe<Boolean> update = new Probe<>();
        gateway.setRuleEnabled("important", false, update);
        assertTrue(update.awaitSuccess());
        assertTrue(update.callbackOnUiThread.get());

        Probe<List<WhiteListRuleSnapshot>> secondRead = new Probe<>();
        gateway.getRules(secondRead);
        List<WhiteListRuleSnapshot> secondRules = secondRead.awaitSuccess();
        assertEquals(1, secondRules.size());
        assertFalse(secondRules.get(0).isEnabled());

        Probe<Boolean> missingRemove = new Probe<>();
        gateway.removeRule("missing", missingRemove);
        assertFalse(missingRemove.awaitSuccess());

        Probe<Boolean> missingUpdate = new Probe<>();
        gateway.setRuleEnabled("missing", true, missingUpdate);
        assertFalse(missingUpdate.awaitSuccess());

        Probe<Boolean> remove = new Probe<>();
        gateway.removeRule("important", remove);
        assertTrue(remove.awaitSuccess());

        Probe<List<WhiteListRuleSnapshot>> finalRead = new Probe<>();
        gateway.getRules(finalRead);
        assertEquals(0, finalRead.awaitSuccess().size());
    }

    @Test
    public void testCallerDoesNotBlockWhileStorageQueueIsBlocked() throws Exception {
        fixture.initializeReady();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        fixture.registerBlockerRelease(release);
        fixture.queue().postRunnable(() -> {
            entered.countDown();
            await(release);
        });
        assertTrue("Storage queue did not enter blocker", entered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        Probe<List<WhiteListRuleSnapshot>> probe = new Probe<>();
        CountDownLatch callerDone = new CountDownLatch(1);
        Thread caller = new Thread(() -> {
            fixture.gateway().getRules(probe);
            callerDone.countDown();
        }, "cleargram-management-caller");
        caller.start();

        assertTrue("Gateway caller blocked on storage queue",
                callerDone.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertEquals(0, probe.terminalCount.get());

        release.countDown();
        fixture.clearBlockerRelease();
        probe.awaitSuccess();
        assertTrue(probe.callbackOnUiThread.get());
    }

    @Test
    public void testValidationFailureIsDeliveredOnceOnUiThread() throws Exception {
        fixture.initializeReady();

        Probe<WhiteListRuleSnapshot> probe = new Probe<>();
        fixture.gateway().addRule("   ", true, probe);

        RuntimeException failure = probe.awaitFailure();
        assertTrue(failure instanceof IllegalArgumentException);
        assertTrue(probe.callbackOnUiThread.get());
        assertEquals(0, probe.successCount.get());
        assertEquals(1, probe.failureCount.get());

        Probe<List<WhiteListRuleSnapshot>> read = new Probe<>();
        fixture.gateway().getRules(read);
        assertEquals(0, read.awaitSuccess().size());
    }

    @Test
    public void testCallbackExceptionDoesNotTriggerSecondCallback() throws Exception {
        fixture.initializeReady();

        CountDownLatch reached = new CountDownLatch(1);
        AtomicInteger terminalCount = new AtomicInteger();
        fixture.gateway().getRules(new TelegramWhiteListManagementGateway.Callback<List<WhiteListRuleSnapshot>>() {
            @Override
            public void onSuccess(List<WhiteListRuleSnapshot> result) {
                terminalCount.incrementAndGet();
                reached.countDown();
                throw new RuntimeException("callback failure");
            }

            @Override
            public void onFailure(RuntimeException error) {
                terminalCount.incrementAndGet();
            }
        });

        assertTrue("Success callback was not reached", reached.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> { });
        assertEquals(1, terminalCount.get());
    }

    private static final class Probe<T> implements TelegramWhiteListManagementGateway.Callback<T> {

        private final AtomicInteger successCount = new AtomicInteger();
        private final AtomicInteger failureCount = new AtomicInteger();
        private final AtomicInteger terminalCount = new AtomicInteger();
        private final AtomicReference<T> result = new AtomicReference<>();
        private final AtomicReference<RuntimeException> failure = new AtomicReference<>();
        private final AtomicReference<Boolean> callbackOnUiThread = new AtomicReference<>(false);
        private final CountDownLatch terminal = new CountDownLatch(1);

        @Override
        public void onSuccess(T value) {
            callbackOnUiThread.set(Looper.myLooper() == Looper.getMainLooper());
            result.set(value);
            successCount.incrementAndGet();
            terminalCount.incrementAndGet();
            terminal.countDown();
        }

        @Override
        public void onFailure(RuntimeException error) {
            callbackOnUiThread.set(Looper.myLooper() == Looper.getMainLooper());
            failure.set(error);
            failureCount.incrementAndGet();
            terminalCount.incrementAndGet();
            terminal.countDown();
        }

        T awaitSuccess() throws Exception {
            awaitTerminal();
            if (failure.get() != null) {
                throw failure.get();
            }
            assertEquals(1, successCount.get());
            assertEquals(0, failureCount.get());
            assertEquals(1, terminalCount.get());
            return result.get();
        }

        RuntimeException awaitFailure() throws Exception {
            awaitTerminal();
            assertEquals(0, successCount.get());
            assertEquals(1, failureCount.get());
            assertEquals(1, terminalCount.get());
            assertNotNull(failure.get());
            return failure.get();
        }

        private void awaitTerminal() throws Exception {
            assertTrue("Timed out waiting for UI callback",
                    terminal.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        }
    }

    private static final class Fixture implements AutoCloseable {

        private final File directory;
        private final AtomicReference<CountDownLatch> blockerRelease = new AtomicReference<>();

        private Fixture(File directory) {
            this.directory = directory;
        }

        void initializeReady() throws Exception {
            TelegramNoiseBootstrap.initialize(directory);
            assertTrue(NoiseBootstrap.isInitialized());
            barrier(queue());
        }

        TelegramWhiteListManagementGateway gateway() {
            return TelegramNoiseBootstrap.getWhiteListManagementGateway();
        }

        DispatchQueue queue() throws Exception {
            DispatchQueue queue = getStorageQueue();
            assertNotNull(queue);
            return queue;
        }

        TelegramWhiteListManagementGateway getPublishedGateway() throws Exception {
            return (TelegramWhiteListManagementGateway) getPlatformField("whiteListManagementGateway");
        }

        void registerBlockerRelease(CountDownLatch release) {
            blockerRelease.set(release);
        }

        void clearBlockerRelease() {
            blockerRelease.set(null);
        }

        @Override
        public void close() throws Exception {
            CountDownLatch release = blockerRelease.getAndSet(null);
            if (release != null) {
                release.countDown();
            }
            cleanupPlatformBootstrap();
            setPlatformField("whiteListManagementGateway", null);
            deleteStorageFiles(directory);
        }
    }

    private static void cleanupPlatformBootstrap() throws Exception {
        TelegramWhiteListStorageAdapter adapter = getStorageAdapter();
        DispatchQueue queue = getStorageQueue();
        if (queue != null) {
            barrier(queue);
            if (adapter != null) {
                runOnQueue(queue, adapter::close);
            }
            barrier(queue);
            queue.recycle();
            queue.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
            assertFalse("Storage queue did not terminate", queue.isAlive());
        }
        setPlatformField("whiteListManagementGateway", null);
        setPlatformField("storageAdapter", null);
        setPlatformField("storageQueue", null);
        setPlatformInitialized(false);
    }

    private static void barrier(DispatchQueue queue) throws Exception {
        runOnQueue(queue, () -> { });
    }

    private static void runOnQueue(DispatchQueue queue, Runnable runnable) throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        assertTrue("Storage queue rejected runnable", queue.postRunnable(() -> {
            try {
                runnable.run();
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                completed.countDown();
            }
        }));
        assertTrue("Timed out waiting for storage queue", completed.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        rethrow(failure.get());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError(error);
        }
    }

    private static File createDirectory(String prefix) {
        File root = InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir();
        File directory = new File(root, prefix + System.nanoTime());
        assertTrue(directory.mkdirs());
        return directory;
    }

    private static DispatchQueue getStorageQueue() throws Exception {
        return (DispatchQueue) getPlatformField("storageQueue");
    }

    private static TelegramWhiteListStorageAdapter getStorageAdapter() throws Exception {
        return (TelegramWhiteListStorageAdapter) getPlatformField("storageAdapter");
    }

    private static Object getPlatformField(String name) throws Exception {
        Field field = TelegramNoiseBootstrap.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    private static void setPlatformField(String name, Object value) throws Exception {
        Field field = TelegramNoiseBootstrap.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    private static void setPlatformInitialized(boolean initialized) throws Exception {
        Field field = TelegramNoiseBootstrap.class.getDeclaredField("initialized");
        field.setAccessible(true);
        field.setBoolean(null, initialized);
    }

    private static void resetNoiseBootstrap() throws Exception {
        Field initialized = NoiseBootstrap.class.getDeclaredField("initialized");
        initialized.setAccessible(true);
        initialized.setBoolean(null, false);
        Field core = NoiseBootstrap.class.getDeclaredField("core");
        core.setAccessible(true);
        core.set(null, null);
    }

    private static void deleteStorageFiles(File directory) {
        if (directory == null) {
            return;
        }
        delete(new File(directory, "noisegram.db"));
        delete(new File(directory, "noisegram.db-journal"));
        delete(new File(directory, "noisegram.db-wal"));
        delete(new File(directory, "noisegram.db-shm"));
        delete(directory);
    }

    private static void delete(File file) {
        if (file.exists() && !file.delete()) {
            file.deleteOnExit();
        }
    }

    private static void rethrow(Throwable throwable) throws Exception {
        if (throwable == null) {
            return;
        }
        if (throwable instanceof Exception) {
            throw (Exception) throwable;
        }
        if (throwable instanceof Error) {
            throw (Error) throwable;
        }
        throw new AssertionError(throwable);
    }
}

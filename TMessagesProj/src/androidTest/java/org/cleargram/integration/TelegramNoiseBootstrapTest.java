package org.cleargram.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.cleargram.api.BootstrapConfiguration;
import org.cleargram.api.NoiseBootstrap;
import org.cleargram.api.NoiseCore;
import org.cleargram.storage.telegram.TelegramBlackListStorageAdapter;
import org.cleargram.storage.telegram.TelegramDuplicateVideoStorageAdapter;
import org.cleargram.storage.telegram.TelegramWhiteListStorageAdapter;
import org.telegram.messenger.DispatchQueue;

import java.io.File;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public final class TelegramNoiseBootstrapTest {

    private static final long TIMEOUT_SECONDS = 15;

    private File testDirectory;

    @Before
    public void setUp() throws Exception {
        cleanupPlatformBootstrap();
        resetNoiseBootstrap();
        testDirectory = createDirectory("cleargram_bootstrap_");
    }

    @After
    public void tearDown() throws Exception {
        try {
            cleanupPlatformBootstrap();
        } finally {
            resetNoiseBootstrap();
            deleteStorageFiles(testDirectory);
        }
    }

    @Test
    public void testInitializesPersistentCoreAtExactDatabasePath() throws Exception {
        TelegramNoiseBootstrap.initialize(testDirectory);

        assertTrue(NoiseBootstrap.isInitialized());
        assertTrue(getPlatformInitialized());
        DispatchQueue queue = getStorageQueue();
        assertNotNull(queue);
        assertNotNull(getStorageAdapter());
        barrier(queue);

        NoiseCore core = NoiseBootstrap.getCore();
        assertEquals(0, core.getWhiteListRules().size());

        File databaseFile = new File(testDirectory, "noisegram.db");
        assertTrue(databaseFile.isFile());
        assertFalse(new File(testDirectory, "noisegram_0.db").exists());
        File[] databaseFiles = testDirectory.listFiles((directory, name) -> name.endsWith(".db"));
        assertNotNull(databaseFiles);
        assertEquals(1, databaseFiles.length);
        assertEquals("noisegram.db", databaseFiles[0].getName());
    }

    @Test
    public void testAllPersistentRuleFamiliesShareProductionDatabaseAndQueue() throws Exception {
        TelegramNoiseBootstrap.initialize(testDirectory);
        DispatchQueue queue = getStorageQueue();
        barrier(queue);

        TelegramWhiteListStorageAdapter white = getStorageAdapter();
        TelegramBlackListStorageAdapter black = (TelegramBlackListStorageAdapter) getPlatformField(
                "blackListStorageAdapter");
        TelegramDuplicateVideoStorageAdapter duplicate = getDuplicateVideoStorageAdapter(NoiseBootstrap.getCore());

        assertNotNull(white);
        assertNotNull(black);
        assertNotNull(duplicate);
        assertSame(queue, getInstanceField(black, "storageQueue"));
        assertSame(queue, getInstanceField(duplicate, "storageQueue"));
        Object database = getInstanceField(white, "database");
        assertSame(database, getInstanceField(black, "database"));
        assertSame(database, getInstanceField(duplicate, "database"));
    }

    @Test
    public void testRepeatedInitializeReusesSameResourcesAndCore() throws Exception {
        TelegramNoiseBootstrap.initialize(testDirectory);
        DispatchQueue firstQueue = getStorageQueue();
        TelegramWhiteListStorageAdapter firstAdapter = getStorageAdapter();
        NoiseCore firstCore = NoiseBootstrap.getCore();
        barrier(firstQueue);

        File secondDirectory = createDirectory("cleargram_bootstrap_second_");
        try {
            TelegramNoiseBootstrap.initialize(secondDirectory);

            assertSame(firstCore, NoiseBootstrap.getCore());
            assertSame(firstQueue, getStorageQueue());
            assertSame(firstAdapter, getStorageAdapter());
            assertFalse(new File(secondDirectory, "noisegram.db").exists());
        } finally {
            deleteStorageFiles(secondDirectory);
        }
    }

    @Test
    public void testPreinitializedCoreIsReportedWithoutCreatingPlatformResources() throws Exception {
        NoiseBootstrap.initialize(new BootstrapConfiguration());
        NoiseCore directCore = NoiseBootstrap.getCore();

        TelegramNoiseBootstrap.initialize(testDirectory);

        assertSame(directCore, NoiseBootstrap.getCore());
        assertFalse(getPlatformInitialized());
        assertNull(getStorageQueue());
        assertNull(getStorageAdapter());
        assertFalse(new File(testDirectory, "noisegram.db").exists());
    }

    @Test
    public void testInvalidFilesDirectoryCreatesNoResourcesAndDoesNotInitializeCore() throws Exception {
        TelegramNoiseBootstrap.initialize(null);

        assertFalse(NoiseBootstrap.isInitialized());
        assertFalse(getPlatformInitialized());
        assertNull(getStorageQueue());
        assertNull(getStorageAdapter());
        assertFalse(new File(testDirectory, "noisegram.db").exists());
    }

    private static File createDirectory(String prefix) {
        File root = InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir();
        File directory = new File(root, prefix + System.nanoTime());
        assertTrue(directory.mkdirs());
        return directory;
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
        queue.postRunnable(() -> {
            try {
                runnable.run();
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                completed.countDown();
            }
        });
        assertTrue("Timed out waiting for storage queue", completed.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        rethrow(failure.get());
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

    private static boolean getPlatformInitialized() throws Exception {
        Field field = TelegramNoiseBootstrap.class.getDeclaredField("initialized");
        field.setAccessible(true);
        return field.getBoolean(null);
    }

    private static void setPlatformInitialized(boolean initialized) throws Exception {
        Field field = TelegramNoiseBootstrap.class.getDeclaredField("initialized");
        field.setAccessible(true);
        field.setBoolean(null, initialized);
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

    private static TelegramDuplicateVideoStorageAdapter getDuplicateVideoStorageAdapter(NoiseCore core)
            throws Exception {
        Object manager = getInstanceField(core, "duplicateVideoManager");
        Object repository = getInstanceField(manager, "repository");
        return (TelegramDuplicateVideoStorageAdapter) getInstanceField(repository, "storagePort");
    }

    private static Object getInstanceField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setPlatformField(String name, Object value) throws Exception {
        Field field = TelegramNoiseBootstrap.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
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
}

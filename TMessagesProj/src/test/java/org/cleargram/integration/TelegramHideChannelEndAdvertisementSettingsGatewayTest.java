package org.cleargram.integration;

import java.util.ArrayDeque;

import org.junit.Test;
import org.cleargram.storage.telegram.TelegramHideChannelEndAdvertisementStorage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class TelegramHideChannelEndAdvertisementSettingsGatewayTest {

    @Test
    public void defaultLoadedStateIsDisabled() {
        Fixture fixture = new Fixture();

        fixture.gateway.startInitialLoad();
        fixture.storage.loaded(false);

        assertFalse(fixture.gateway.isHideChannelEndAdvertisementEnabled());
    }

    @Test
    public void successfulToggleWritesBeforePublishingTheReadOnlyState() {
        Fixture fixture = readyFixture(false);
        fixture.storage.beforeWrite = () -> assertFalse(
                fixture.gateway.isHideChannelEndAdvertisementEnabled());
        RecordingCallback callback = new RecordingCallback();

        fixture.gateway.setHideChannelEndAdvertisementEnabled(true, callback);
        fixture.queue.runAll();

        assertEquals(1, fixture.storage.writeCalls);
        assertTrue(fixture.storage.lastEnabled);
        assertTrue(fixture.gateway.isHideChannelEndAdvertisementEnabled());
        assertTrue(callback.success);
    }

    @Test
    public void failedTogglePreservesThePreviousPublishedState() {
        Fixture fixture = readyFixture(false);
        fixture.storage.writeFailure = new IllegalStateException("write failure");
        RecordingCallback callback = new RecordingCallback();

        fixture.gateway.setHideChannelEndAdvertisementEnabled(true, callback);
        fixture.queue.runAll();

        assertFalse(fixture.gateway.isHideChannelEndAdvertisementEnabled());
        assertTrue(callback.failed);
    }

    @Test
    public void failedInitialLoadFailsOpen() {
        Fixture fixture = new Fixture();

        fixture.gateway.startInitialLoad();
        fixture.storage.failed(new IllegalStateException("load failure"));

        assertFalse(fixture.gateway.isHideChannelEndAdvertisementEnabled());
    }

    private static Fixture readyFixture(boolean enabled) {
        Fixture fixture = new Fixture();
        fixture.gateway.startInitialLoad();
        fixture.storage.loaded(enabled);
        return fixture;
    }

    private static final class Fixture {
        final FakeStorage storage = new FakeStorage();
        final ManualQueue queue = new ManualQueue();
        final TelegramHideChannelEndAdvertisementSettingsGateway gateway =
                new TelegramHideChannelEndAdvertisementSettingsGateway(storage, queue, Runnable::run);
    }

    private static final class FakeStorage
            implements TelegramHideChannelEndAdvertisementSettingsGateway.StorageOperations {
        TelegramHideChannelEndAdvertisementStorage.LoadCallback callback;
        Runnable beforeWrite;
        RuntimeException writeFailure;
        int writeCalls;
        boolean lastEnabled;

        @Override
        public void load(TelegramHideChannelEndAdvertisementStorage.LoadCallback callback) {
            this.callback = callback;
        }

        @Override
        public void setEnabled(boolean enabled) {
            writeCalls++;
            if (beforeWrite != null) {
                beforeWrite.run();
            }
            if (writeFailure != null) {
                throw writeFailure;
            }
            lastEnabled = enabled;
        }

        void loaded(boolean enabled) {
            callback.onLoaded(enabled);
        }

        void failed(RuntimeException error) {
            callback.onFailed(error);
        }
    }

    private static final class ManualQueue
            implements TelegramHideChannelEndAdvertisementSettingsGateway.QueueOperations {
        private final ArrayDeque<Runnable> operations = new ArrayDeque<>();

        @Override
        public boolean post(Runnable runnable) {
            operations.addLast(runnable);
            return true;
        }

        void runAll() {
            while (!operations.isEmpty()) {
                operations.removeFirst().run();
            }
        }
    }

    private static final class RecordingCallback
            implements TelegramHideChannelEndAdvertisementSettingsGateway.Callback<Void> {
        boolean success;
        boolean failed;

        @Override
        public void onSuccess(Void result) {
            success = true;
        }

        @Override
        public void onFailure(RuntimeException error) {
            failed = true;
        }
    }
}

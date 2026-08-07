package org.cleargram.integration;

import java.util.ArrayDeque;

import org.junit.Test;
import org.cleargram.storage.telegram.TelegramHideFullscreenVideoAdvertisementStorage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class TelegramHideFullscreenVideoAdvertisementSettingsGatewayTest {

    @Test
    public void defaultLoadedStateIsDisabled() {
        Fixture fixture = new Fixture();

        fixture.gateway.startInitialLoad();
        fixture.storage.loaded(false);

        assertFalse(fixture.gateway.shouldHideFullscreenVideoAdvertisement());
    }

    @Test
    public void managementStateReportsTheConfirmedLoadedValue() {
        Fixture fixture = readyFixture(true);
        RecordingManagementCallback callback = new RecordingManagementCallback();

        fixture.gateway.getManagementState(callback);
        fixture.queue.runAll();

        assertTrue(callback.state.isReady());
        assertTrue(callback.state.isEnabled());
    }

    @Test
    public void successfulToggleWritesBeforePublishingTheReadOnlyState() {
        Fixture fixture = readyFixture(false);
        fixture.storage.beforeWrite = () -> assertFalse(
                fixture.gateway.shouldHideFullscreenVideoAdvertisement());
        RecordingCallback callback = new RecordingCallback();

        fixture.gateway.setHideFullscreenVideoAdvertisementEnabled(true, callback);
        fixture.queue.runAll();

        assertEquals(1, fixture.storage.writeCalls);
        assertTrue(fixture.storage.lastEnabled);
        assertTrue(fixture.gateway.shouldHideFullscreenVideoAdvertisement());
        assertTrue(callback.success);
    }

    @Test
    public void failedTogglePreservesThePreviousPublishedState() {
        Fixture fixture = readyFixture(false);
        fixture.storage.writeFailure = new IllegalStateException("write failure");
        RecordingCallback callback = new RecordingCallback();

        fixture.gateway.setHideFullscreenVideoAdvertisementEnabled(true, callback);
        fixture.queue.runAll();

        assertFalse(fixture.gateway.shouldHideFullscreenVideoAdvertisement());
        assertTrue(callback.failed);
    }

    @Test
    public void failedInitialLoadFailsOpen() {
        Fixture fixture = new Fixture();

        fixture.gateway.startInitialLoad();
        fixture.storage.failed(new IllegalStateException("load failure"));

        assertFalse(fixture.gateway.shouldHideFullscreenVideoAdvertisement());
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
        final TelegramHideFullscreenVideoAdvertisementSettingsGateway gateway =
                new TelegramHideFullscreenVideoAdvertisementSettingsGateway(
                        storage, queue, Runnable::run);
    }

    private static final class FakeStorage
            implements TelegramHideFullscreenVideoAdvertisementSettingsGateway.StorageOperations {
        TelegramHideFullscreenVideoAdvertisementStorage.LoadCallback callback;
        Runnable beforeWrite;
        RuntimeException writeFailure;
        int writeCalls;
        boolean lastEnabled;

        @Override
        public void load(TelegramHideFullscreenVideoAdvertisementStorage.LoadCallback callback) {
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
            implements TelegramHideFullscreenVideoAdvertisementSettingsGateway.QueueOperations {
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
            implements TelegramHideFullscreenVideoAdvertisementSettingsGateway.Callback<Void> {
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

    private static final class RecordingManagementCallback
            implements TelegramHideFullscreenVideoAdvertisementSettingsGateway.Callback<TelegramHideFullscreenVideoAdvertisementSettingsGateway.ManagementState> {
        TelegramHideFullscreenVideoAdvertisementSettingsGateway.ManagementState state;

        @Override
        public void onSuccess(TelegramHideFullscreenVideoAdvertisementSettingsGateway.ManagementState result) {
            state = result;
        }

        @Override
        public void onFailure(RuntimeException error) {
            throw new AssertionError(error);
        }
    }
}

package org.cleargram.integration;

import java.lang.reflect.Field;
import java.util.ArrayDeque;

import org.junit.Test;
import org.cleargram.storage.telegram.TelegramHideChannelPinnedMessageHeaderStorage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class TelegramHideChannelPinnedMessageHeaderSettingsGatewayTest {

    @Test
    public void defaultLoadedStateIsDisabled() {
        Fixture fixture = new Fixture();

        fixture.gateway.startInitialLoad();
        fixture.storage.loaded(false);

        assertFalse(fixture.gateway.shouldHideChannelPinnedMessageHeader());
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
                fixture.gateway.shouldHideChannelPinnedMessageHeader());
        RecordingCallback callback = new RecordingCallback();

        fixture.gateway.setHideChannelPinnedMessageHeaderEnabled(true, callback);
        fixture.queue.runAll();

        assertEquals(1, fixture.storage.writeCalls);
        assertTrue(fixture.storage.lastEnabled);
        assertTrue(fixture.gateway.shouldHideChannelPinnedMessageHeader());
        assertTrue(callback.success);
    }

    @Test
    public void failedTogglePreservesThePreviousPublishedState() {
        Fixture fixture = readyFixture(false);
        fixture.storage.writeFailure = new IllegalStateException("write failure");
        RecordingCallback callback = new RecordingCallback();

        fixture.gateway.setHideChannelPinnedMessageHeaderEnabled(true, callback);
        fixture.queue.runAll();

        assertFalse(fixture.gateway.shouldHideChannelPinnedMessageHeader());
        assertTrue(callback.failed);
    }

    @Test
    public void failedInitialLoadFailsOpen() {
        Fixture fixture = new Fixture();

        fixture.gateway.startInitialLoad();
        fixture.storage.failed(new IllegalStateException("load failure"));

        assertFalse(fixture.gateway.shouldHideChannelPinnedMessageHeader());
    }

    @Test
    public void bootstrapPolicyAccessorIsFailOpenAndReadsPublishedGatewayWithoutQueueWork()
            throws Exception {
        try {
            setBootstrapPolicyGateway(null);
            assertFalse(TelegramNoiseBootstrap.shouldHideChannelPinnedMessageHeader());

            Fixture loading = new Fixture();
            setBootstrapPolicyGateway(loading.gateway);
            assertFalse(TelegramNoiseBootstrap.shouldHideChannelPinnedMessageHeader());
            assertEquals(0, loading.queue.pendingCount());

            Fixture enabled = readyFixture(true);
            setBootstrapPolicyGateway(enabled.gateway);
            assertTrue(TelegramNoiseBootstrap.shouldHideChannelPinnedMessageHeader());
            assertEquals(0, enabled.queue.pendingCount());

            Fixture failed = new Fixture();
            failed.gateway.startInitialLoad();
            failed.storage.failed(new IllegalStateException("load failure"));
            setBootstrapPolicyGateway(failed.gateway);
            assertFalse(TelegramNoiseBootstrap.shouldHideChannelPinnedMessageHeader());
            assertEquals(0, failed.queue.pendingCount());
        } finally {
            setBootstrapPolicyGateway(null);
        }
    }

    private static void setBootstrapPolicyGateway(
            TelegramHideChannelPinnedMessageHeaderSettingsGateway gateway
    ) throws Exception {
        Field field = TelegramNoiseBootstrap.class.getDeclaredField(
                "hideChannelPinnedMessageHeaderSettingsGateway");
        field.setAccessible(true);
        field.set(null, gateway);
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
        final TelegramHideChannelPinnedMessageHeaderSettingsGateway gateway =
                new TelegramHideChannelPinnedMessageHeaderSettingsGateway(
                        storage, queue, Runnable::run);
    }

    private static final class FakeStorage
            implements TelegramHideChannelPinnedMessageHeaderSettingsGateway.StorageOperations {
        TelegramHideChannelPinnedMessageHeaderStorage.LoadCallback callback;
        Runnable beforeWrite;
        RuntimeException writeFailure;
        int writeCalls;
        boolean lastEnabled;

        @Override
        public void load(TelegramHideChannelPinnedMessageHeaderStorage.LoadCallback callback) {
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
            implements TelegramHideChannelPinnedMessageHeaderSettingsGateway.QueueOperations {
        private final ArrayDeque<Runnable> operations = new ArrayDeque<>();

        @Override
        public boolean post(Runnable runnable) {
            operations.addLast(runnable);
            return true;
        }

        int pendingCount() {
            return operations.size();
        }

        void runAll() {
            while (!operations.isEmpty()) {
                operations.removeFirst().run();
            }
        }
    }

    private static final class RecordingCallback
            implements TelegramHideChannelPinnedMessageHeaderSettingsGateway.Callback<Void> {
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
            implements TelegramHideChannelPinnedMessageHeaderSettingsGateway.Callback<TelegramHideChannelPinnedMessageHeaderSettingsGateway.ManagementState> {
        TelegramHideChannelPinnedMessageHeaderSettingsGateway.ManagementState state;

        @Override
        public void onSuccess(TelegramHideChannelPinnedMessageHeaderSettingsGateway.ManagementState result) {
            state = result;
        }

        @Override
        public void onFailure(RuntimeException error) {
            throw new AssertionError(error);
        }
    }
}

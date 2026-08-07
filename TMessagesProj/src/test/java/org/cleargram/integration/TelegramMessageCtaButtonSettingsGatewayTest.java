package org.cleargram.integration;

import java.util.ArrayDeque;

import org.junit.Test;
import org.cleargram.storage.telegram.TelegramMessageCtaButtonSettingsStorage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class TelegramMessageCtaButtonSettingsGatewayTest {

    @Test
    public void defaultSnapshotFailsOpenUntilTheInitialLoadCompletes() {
        Fixture fixture = new Fixture();
        fixture.gateway.startInitialLoad();

        assertFalse(fixture.gateway.getMessageCtaButtonSettings().isEnabled());
        assertEquals(MessageCtaButtonAction.COLLAPSE,
                fixture.gateway.getMessageCtaButtonSettings().getAction());
    }

    @Test
    public void initialLoadPublishesEnabledAndActionTogether() {
        Fixture fixture = readyFixture(true, MessageCtaButtonAction.HIDE);

        MessageCtaButtonSettingsState state = fixture.gateway.getMessageCtaButtonSettings();
        assertTrue(state.isEnabled());
        assertEquals(MessageCtaButtonAction.HIDE, state.getAction());
    }

    @Test
    public void enabledWriteIsDurableFirstAndPreservesAction() {
        Fixture fixture = readyFixture(false, MessageCtaButtonAction.HIDE);
        fixture.storage.beforeEnabledWrite = () -> assertFalse(
                fixture.gateway.getMessageCtaButtonSettings().isEnabled());
        RecordingCallback callback = new RecordingCallback();

        fixture.gateway.setEnabled(true, callback);
        fixture.queue.runAll();

        assertTrue(callback.success);
        assertTrue(fixture.gateway.getMessageCtaButtonSettings().isEnabled());
        assertEquals(MessageCtaButtonAction.HIDE,
                fixture.gateway.getMessageCtaButtonSettings().getAction());
    }

    @Test
    public void actionWriteIsDurableFirstAndPreservesEnabled() {
        Fixture fixture = readyFixture(true, MessageCtaButtonAction.COLLAPSE);
        fixture.storage.beforeActionWrite = () -> assertEquals(MessageCtaButtonAction.COLLAPSE,
                fixture.gateway.getMessageCtaButtonSettings().getAction());
        RecordingCallback callback = new RecordingCallback();

        fixture.gateway.setAction(MessageCtaButtonAction.HIDE, callback);
        fixture.queue.runAll();

        assertTrue(callback.success);
        assertTrue(fixture.gateway.getMessageCtaButtonSettings().isEnabled());
        assertEquals(MessageCtaButtonAction.HIDE,
                fixture.gateway.getMessageCtaButtonSettings().getAction());
    }

    @Test
    public void writeFailurePreservesThePublishedSnapshotAndNullActionIsRejected() {
        Fixture fixture = readyFixture(true, MessageCtaButtonAction.COLLAPSE);
        fixture.storage.actionFailure = new IllegalStateException("write failure");
        RecordingCallback failed = new RecordingCallback();

        fixture.gateway.setAction(MessageCtaButtonAction.HIDE, failed);
        fixture.queue.runAll();

        assertTrue(failed.failed);
        assertEquals(MessageCtaButtonAction.COLLAPSE,
                fixture.gateway.getMessageCtaButtonSettings().getAction());

        RecordingCallback nullAction = new RecordingCallback();
        fixture.gateway.setAction(null, nullAction);
        assertTrue(nullAction.failed);
        assertEquals(1, fixture.storage.actionWrites);
    }

    private static Fixture readyFixture(boolean enabled, MessageCtaButtonAction action) {
        Fixture fixture = new Fixture();
        fixture.gateway.startInitialLoad();
        fixture.storage.loaded(new MessageCtaButtonSettingsState(enabled, action));
        return fixture;
    }

    private static final class Fixture {
        final FakeStorage storage = new FakeStorage();
        final ManualQueue queue = new ManualQueue();
        final TelegramMessageCtaButtonSettingsGateway gateway =
                new TelegramMessageCtaButtonSettingsGateway(storage, queue, Runnable::run);
    }

    private static final class FakeStorage
            implements TelegramMessageCtaButtonSettingsGateway.StorageOperations {
        TelegramMessageCtaButtonSettingsStorage.LoadCallback callback;
        Runnable beforeEnabledWrite;
        Runnable beforeActionWrite;
        RuntimeException actionFailure;
        int actionWrites;

        @Override
        public void load(TelegramMessageCtaButtonSettingsStorage.LoadCallback callback) {
            this.callback = callback;
        }

        @Override
        public void setEnabled(boolean enabled) {
            if (beforeEnabledWrite != null) {
                beforeEnabledWrite.run();
            }
        }

        @Override
        public void setAction(MessageCtaButtonAction action) {
            actionWrites++;
            if (beforeActionWrite != null) {
                beforeActionWrite.run();
            }
            if (actionFailure != null) {
                throw actionFailure;
            }
        }

        void loaded(MessageCtaButtonSettingsState state) {
            callback.onLoaded(state);
        }
    }

    private static final class ManualQueue
            implements TelegramMessageCtaButtonSettingsGateway.QueueOperations {
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
            implements TelegramMessageCtaButtonSettingsGateway.Callback<MessageCtaButtonSettingsState> {
        boolean success;
        boolean failed;

        @Override
        public void onSuccess(MessageCtaButtonSettingsState result) {
            success = true;
        }

        @Override
        public void onFailure(RuntimeException error) {
            failed = true;
        }
    }
}

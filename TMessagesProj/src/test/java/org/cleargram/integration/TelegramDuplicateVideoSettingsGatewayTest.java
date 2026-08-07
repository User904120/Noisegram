package org.cleargram.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.cleargram.api.DuplicateVideoMatchMode;
import org.cleargram.api.DuplicateVideoRuntimeStatus;
import org.cleargram.api.DuplicateVideoStateSnapshot;
import org.cleargram.api.DuplicateVideoStateSnapshotTestFactory;

public final class TelegramDuplicateVideoSettingsGatewayTest {

    @Test
    public void defaultsToDisabledAndRestoresPersistedValueInNewGateway() {
        FakeCore core = new FakeCore(false);
        Fixture first = new Fixture(core);
        first.gateway.startInitialLoad();
        first.queue.runAll();

        assertTrue(first.gateway.getManagementState().isReady());
        assertFalse(first.gateway.isDuplicateVideoEnabled());

        first.gateway.setDuplicateVideoEnabled(true, new VoidCallback());
        first.queue.runAll();
        assertTrue(first.gateway.isDuplicateVideoEnabled());
        assertEquals(1, core.setEnabledCalls);

        Fixture restored = new Fixture(core);
        restored.gateway.startInitialLoad();
        restored.queue.runAll();
        assertTrue(restored.gateway.getManagementState().isReady());
        assertTrue(restored.gateway.isDuplicateVideoEnabled());
    }

    @Test
    public void persistsBothEnabledValuesWithoutTouchingOtherSettings() {
        FakeCore core = new FakeCore(true);
        Fixture fixture = new Fixture(core);
        fixture.gateway.startInitialLoad();
        fixture.queue.runAll();

        fixture.gateway.setDuplicateVideoEnabled(false, new VoidCallback());
        fixture.queue.runAll();
        assertFalse(core.enabled);
        assertFalse(fixture.gateway.isDuplicateVideoEnabled());
        assertTrue(fixture.gateway.getManagementState().isReady());
        assertFalse(fixture.gateway.getManagementState().isEnabled());

        fixture.gateway.setDuplicateVideoEnabled(true, new VoidCallback());
        fixture.queue.runAll();
        assertTrue(core.enabled);
        assertTrue(fixture.gateway.getManagementState().isReady());
        assertTrue(fixture.gateway.getManagementState().isEnabled());
        assertEquals(DuplicateVideoMatchMode.VIDEO_AND_TEXT, core.mode);
        assertEquals(2, core.setEnabledCalls);
    }

    @Test
    public void readFailureFailsOpenAndWriteFailureKeepsLastPublishedValue() {
        FakeCore core = new FakeCore(true);
        Fixture fixture = new Fixture(core);
        fixture.gateway.startInitialLoad();
        fixture.queue.runAll();

        core.readFailure = new IllegalStateException("read failed");
        fixture.gateway.getManagementState(new StateCallback());
        fixture.queue.runAll();
        assertFalse(fixture.gateway.isDuplicateVideoEnabled());
        assertTrue(fixture.gateway.getManagementState().isFailed());

        core.readFailure = null;
        fixture.gateway.getManagementState(new StateCallback());
        fixture.queue.runAll();
        assertTrue(fixture.gateway.isDuplicateVideoEnabled());
        core.writeFailure = new IllegalStateException("write failed");
        fixture.gateway.setDuplicateVideoEnabled(false, new VoidCallback());
        fixture.queue.runAll();
        assertTrue(fixture.gateway.isDuplicateVideoEnabled());
    }

    @Test
    public void callbacksObserveDurableStateOnlyAfterStorageQueueUpdate() {
        FakeCore core = new FakeCore(false);
        Fixture fixture = new Fixture(core);
        fixture.gateway.startInitialLoad();
        fixture.queue.runAll();
        List<Boolean> callbackStates = new ArrayList<>();

        fixture.gateway.setDuplicateVideoEnabled(true,
                new TelegramDuplicateVideoSettingsGateway.Callback<Void>() {
                    @Override public void onSuccess(Void result) {
                        callbackStates.add(fixture.gateway.isDuplicateVideoEnabled());
                    }

                    @Override public void onFailure(RuntimeException error) {
                        fail("Unexpected failure: " + error);
                    }
                });
        assertFalse(fixture.gateway.isDuplicateVideoEnabled());
        fixture.queue.runAll();

        assertEquals(1, callbackStates.size());
        assertTrue(callbackStates.get(0));
    }

    private static final class Fixture {
        final ManualQueue queue = new ManualQueue();
        final TelegramDuplicateVideoSettingsGateway gateway;

        Fixture(FakeCore core) {
            gateway = new TelegramDuplicateVideoSettingsGateway(core, queue, Runnable::run);
        }
    }

    private static final class FakeCore implements TelegramDuplicateVideoSettingsGateway.CoreOperations {
        boolean enabled;
        DuplicateVideoMatchMode mode = DuplicateVideoMatchMode.VIDEO_AND_TEXT;
        int setEnabledCalls;
        RuntimeException readFailure;
        RuntimeException writeFailure;

        FakeCore(boolean enabled) {
            this.enabled = enabled;
        }

        @Override public DuplicateVideoStateSnapshot getState() {
            if (readFailure != null) throw readFailure;
            return DuplicateVideoStateSnapshotTestFactory.create(enabled
                    ? DuplicateVideoRuntimeStatus.READY : DuplicateVideoRuntimeStatus.DISABLED, mode);
        }

        @Override public void setEnabled(boolean enabled) {
            if (writeFailure != null) throw writeFailure;
            setEnabledCalls++;
            this.enabled = enabled;
        }
    }

    private static final class ManualQueue implements TelegramDuplicateVideoSettingsGateway.QueueOperations {
        private final ArrayDeque<Runnable> operations = new ArrayDeque<>();

        @Override public boolean post(Runnable runnable) {
            operations.addLast(runnable);
            return true;
        }

        void runAll() {
            while (!operations.isEmpty()) operations.removeFirst().run();
        }
    }

    private static final class VoidCallback implements TelegramDuplicateVideoSettingsGateway.Callback<Void> {
        @Override public void onSuccess(Void result) { }
        @Override public void onFailure(RuntimeException error) { fail("Unexpected failure: " + error); }
    }

    private static final class StateCallback
            implements TelegramDuplicateVideoSettingsGateway.Callback<TelegramDuplicateVideoSettingsGateway.ManagementState> {
        @Override public void onSuccess(TelegramDuplicateVideoSettingsGateway.ManagementState result) { }
        @Override public void onFailure(RuntimeException error) { fail("Unexpected failure: " + error); }
    }
}

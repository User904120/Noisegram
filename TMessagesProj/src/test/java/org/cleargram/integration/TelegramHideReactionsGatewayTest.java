package org.cleargram.integration;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Modifier;
import java.lang.reflect.Field;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.cleargram.storage.telegram.TelegramHideReactionsStorage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class TelegramHideReactionsGatewayTest {

    @Before
    public void clearBootstrapPolicyGatewayBeforeTest() throws Exception {
        setBootstrapPolicyGateway(null);
    }

    @After
    public void clearBootstrapPolicyGatewayAfterTest() throws Exception {
        setBootstrapPolicyGateway(null);
    }

    @Test
    public void loadingStateFailsOpenUntilInitialLoadCompletes() {
        Fixture fixture = new Fixture();

        fixture.gateway.startInitialLoad();

        assertFalse(fixture.gateway.getManagementState().isReady());
        assertFalse(fixture.gateway.getManagementState().isFailed());
        assertFalse(fixture.gateway.shouldHideReactions());
        fixture.storage.loaded(true);
        assertTrue(fixture.gateway.getManagementState().isReady());
        assertTrue(fixture.gateway.getManagementState().isEnabled());
        assertTrue(fixture.gateway.shouldHideReactions());
    }

    @Test
    public void successfulInitialLoadPublishesBothReadyValues() {
        Fixture disabled = new Fixture();
        disabled.gateway.startInitialLoad();
        disabled.storage.loaded(false);
        assertTrue(disabled.gateway.getManagementState().isReady());
        assertFalse(disabled.gateway.getManagementState().isEnabled());
        assertFalse(disabled.gateway.shouldHideReactions());

        Fixture enabled = new Fixture();
        enabled.gateway.startInitialLoad();
        enabled.storage.loaded(true);
        assertTrue(enabled.gateway.getManagementState().isReady());
        assertTrue(enabled.gateway.getManagementState().isEnabled());
        assertTrue(enabled.gateway.shouldHideReactions());
    }

    @Test
    public void initialLoadFailurePublishesFailedAndFailsOpen() {
        Fixture fixture = new Fixture();
        fixture.gateway.startInitialLoad();

        fixture.storage.failed(new IllegalStateException("load failure"));

        assertFalse(fixture.gateway.getManagementState().isReady());
        assertTrue(fixture.gateway.getManagementState().isFailed());
        assertFalse(fixture.gateway.shouldHideReactions());
    }

    @Test
    public void successfulWriteIsDurableFirstThenPublishesStateThenCallsBack() {
        Fixture fixture = readyFixture(false);
        List<String> events = new ArrayList<>();
        fixture.storage.beforeWrite = () -> {
            events.add("write");
            assertFalse(fixture.gateway.shouldHideReactions());
        };

        fixture.gateway.setHideReactionsEnabled(true, new RecordingCallback(events, fixture.gateway));
        fixture.queue.runAll();

        assertEquals(1, fixture.storage.writeCalls);
        assertTrue(fixture.gateway.shouldHideReactions());
        assertEquals("write", events.get(0));
        assertEquals("success:true", events.get(1));
    }

    @Test
    public void failedWriteKeepsPreviousReadySnapshotAndCallsFailureOnce() {
        Fixture fixture = readyFixture(true);
        fixture.storage.writeFailure = new IllegalStateException("write failure");
        List<String> events = new ArrayList<>();

        fixture.gateway.setHideReactionsEnabled(false, new RecordingCallback(events, fixture.gateway));
        fixture.queue.runAll();

        assertEquals(1, fixture.storage.writeCalls);
        assertTrue(fixture.gateway.getManagementState().isReady());
        assertTrue(fixture.gateway.getManagementState().isEnabled());
        assertTrue(fixture.gateway.shouldHideReactions());
        assertEquals(1, events.size());
        assertEquals("failure", events.get(0));
    }

    @Test
    public void repeatedCurrentValueSucceedsWithoutDurableWrite() {
        Fixture fixture = readyFixture(false);
        List<String> events = new ArrayList<>();

        fixture.gateway.setHideReactionsEnabled(false, new RecordingCallback(events, fixture.gateway));
        fixture.queue.runAll();

        assertEquals(0, fixture.storage.writeCalls);
        assertFalse(fixture.gateway.shouldHideReactions());
        assertEquals(1, events.size());
        assertEquals("success:false", events.get(0));
    }

    @Test
    public void writeAfterFailureCanPublishTheNextDurableSuccess() {
        Fixture fixture = readyFixture(false);
        List<String> events = new ArrayList<>();
        fixture.storage.writeFailure = new IllegalStateException("write failure");

        fixture.gateway.setHideReactionsEnabled(true, new RecordingCallback(events, fixture.gateway));
        fixture.queue.runAll();
        assertFalse(fixture.gateway.shouldHideReactions());
        assertTrue(fixture.gateway.getManagementState().isReady());

        fixture.storage.writeFailure = null;
        fixture.gateway.setHideReactionsEnabled(true, new RecordingCallback(events, fixture.gateway));
        fixture.queue.runAll();

        assertEquals(2, fixture.storage.writeCalls);
        assertTrue(fixture.gateway.shouldHideReactions());
        assertTrue(fixture.gateway.getManagementState().isEnabled());
        assertEquals("failure", events.get(0));
        assertEquals("success:true", events.get(1));
    }

    @Test
    public void sequentialWritesFollowSharedStorageQueueOrder() {
        Fixture fixture = readyFixture(false);
        List<String> events = new ArrayList<>();

        fixture.gateway.setHideReactionsEnabled(true, new RecordingCallback(events, fixture.gateway));
        fixture.gateway.setHideReactionsEnabled(false, new RecordingCallback(events, fixture.gateway));
        fixture.queue.runAll();

        assertEquals(2, fixture.storage.writes.size());
        assertTrue(fixture.storage.writes.get(0));
        assertFalse(fixture.storage.writes.get(1));
        assertFalse(fixture.gateway.shouldHideReactions());
        assertEquals(2, events.size());
        assertEquals("success:true", events.get(0));
        assertEquals("success:false", events.get(1));
    }

    @Test
    public void queuedManagementReadReturnsLoadingWithoutChangingStateOrReadingStorage() {
        ManualCallbackDispatcher dispatcher = new ManualCallbackDispatcher();
        Fixture fixture = new Fixture(dispatcher);
        List<TelegramHideReactionsGateway.ManagementState> results = new ArrayList<>();

        fixture.gateway.startInitialLoad();
        fixture.gateway.getManagementState(new ManagementCallback(results));
        fixture.queue.runAll();

        assertEquals(1, fixture.storage.loadCalls);
        assertTrue(results.isEmpty());
        dispatcher.runAll();

        assertEquals(1, results.size());
        assertFalse(results.get(0).isReady());
        assertFalse(results.get(0).isFailed());
        assertFalse(results.get(0).isEnabled());
        assertFalse(fixture.gateway.getManagementState().isReady());
    }

    @Test
    public void queuedManagementReadReturnsReadyAndFailedStatesExactlyOnce() {
        Fixture disabled = readyFixture(false);
        List<TelegramHideReactionsGateway.ManagementState> disabledResults = new ArrayList<>();
        disabled.gateway.getManagementState(new ManagementCallback(disabledResults));
        disabled.queue.runAll();
        assertEquals(1, disabledResults.size());
        assertTrue(disabledResults.get(0).isReady());
        assertFalse(disabledResults.get(0).isEnabled());

        Fixture enabled = readyFixture(true);
        List<TelegramHideReactionsGateway.ManagementState> enabledResults = new ArrayList<>();
        enabled.gateway.getManagementState(new ManagementCallback(enabledResults));
        enabled.queue.runAll();
        assertEquals(1, enabledResults.size());
        assertTrue(enabledResults.get(0).isReady());
        assertTrue(enabledResults.get(0).isEnabled());

        Fixture failed = new Fixture();
        failed.gateway.startInitialLoad();
        failed.storage.failed(new IllegalStateException("load failure"));
        List<TelegramHideReactionsGateway.ManagementState> failedResults = new ArrayList<>();
        failed.gateway.getManagementState(new ManagementCallback(failedResults));
        failed.queue.runAll();
        assertEquals(1, failedResults.size());
        assertFalse(failedResults.get(0).isReady());
        assertTrue(failedResults.get(0).isFailed());
    }

    @Test
    public void lifecycleImplementationTypesAreNotPublic() {
        for (Class<?> type : TelegramHideReactionsGateway.class.getDeclaredClasses()) {
            assertFalse("Internal lifecycle type must not be public: " + type.getSimpleName(),
                    ("State".equals(type.getSimpleName()) || "Snapshot".equals(type.getSimpleName()))
                            && Modifier.isPublic(type.getModifiers()));
        }
    }

    @Test
    public void bootstrapPolicyAccessorIsFailOpenAndReadsPublishedGatewayWithoutQueueWork() throws Exception {
        assertFalse(TelegramNoiseBootstrap.shouldHideReactions());

        Fixture loading = new Fixture();
        setBootstrapPolicyGateway(loading.gateway);
        assertFalse(TelegramNoiseBootstrap.shouldHideReactions());
        assertEquals(0, loading.queue.pendingCount());

        Fixture disabled = readyFixture(false);
        setBootstrapPolicyGateway(disabled.gateway);
        assertFalse(TelegramNoiseBootstrap.shouldHideReactions());
        assertEquals(0, disabled.queue.pendingCount());

        Fixture enabled = readyFixture(true);
        setBootstrapPolicyGateway(enabled.gateway);
        assertTrue(TelegramNoiseBootstrap.shouldHideReactions());
        assertEquals(0, enabled.queue.pendingCount());

        Fixture failed = new Fixture();
        failed.gateway.startInitialLoad();
        failed.storage.failed(new IllegalStateException("load failure"));
        setBootstrapPolicyGateway(failed.gateway);
        assertFalse(TelegramNoiseBootstrap.shouldHideReactions());
        assertEquals(0, failed.queue.pendingCount());
    }

    private static void setBootstrapPolicyGateway(TelegramHideReactionsGateway gateway) throws Exception {
        Field field = TelegramNoiseBootstrap.class.getDeclaredField("hideReactionsGateway");
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
        final TelegramHideReactionsGateway gateway;

        Fixture() {
            this(Runnable::run);
        }

        Fixture(TelegramHideReactionsGateway.CallbackDispatcher callbackDispatcher) {
            gateway = new TelegramHideReactionsGateway(storage, queue, callbackDispatcher);
        }
    }

    private static final class FakeStorage implements TelegramHideReactionsGateway.StorageOperations {
        TelegramHideReactionsStorage.LoadCallback callback;
        Runnable beforeWrite;
        RuntimeException writeFailure;
        int writeCalls;
        int loadCalls;
        final List<Boolean> writes = new ArrayList<>();

        @Override
        public void load(TelegramHideReactionsStorage.LoadCallback callback) {
            loadCalls++;
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
            writes.add(enabled);
        }

        void loaded(boolean enabled) {
            if (callback == null) {
                fail("Load callback is not registered");
            }
            callback.onLoaded(enabled);
        }

        void failed(RuntimeException error) {
            if (callback == null) {
                fail("Load callback is not registered");
            }
            callback.onFailed(error);
        }
    }

    private static final class ManagementCallback implements TelegramHideReactionsGateway.Callback<TelegramHideReactionsGateway.ManagementState> {
        private final List<TelegramHideReactionsGateway.ManagementState> results;

        ManagementCallback(List<TelegramHideReactionsGateway.ManagementState> results) {
            this.results = results;
        }

        @Override
        public void onSuccess(TelegramHideReactionsGateway.ManagementState result) {
            results.add(result);
        }

        @Override
        public void onFailure(RuntimeException error) {
            fail("Management read failed: " + error);
        }
    }

    private static final class ManualCallbackDispatcher implements TelegramHideReactionsGateway.CallbackDispatcher {
        private final ArrayDeque<Runnable> callbacks = new ArrayDeque<>();

        @Override
        public void dispatch(Runnable runnable) {
            callbacks.addLast(runnable);
        }

        void runAll() {
            while (!callbacks.isEmpty()) {
                callbacks.removeFirst().run();
            }
        }
    }

    private static final class ManualQueue implements TelegramHideReactionsGateway.QueueOperations {
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

        int pendingCount() {
            return operations.size();
        }
    }

    private static final class RecordingCallback implements TelegramHideReactionsGateway.Callback<Void> {
        private final List<String> events;
        private final TelegramHideReactionsGateway gateway;

        RecordingCallback(List<String> events, TelegramHideReactionsGateway gateway) {
            this.events = events;
            this.gateway = gateway;
        }

        @Override
        public void onSuccess(Void ignored) {
            events.add("success:" + gateway.shouldHideReactions());
        }

        @Override
        public void onFailure(RuntimeException error) {
            events.add("failure");
        }
    }
}

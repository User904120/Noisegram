package org.cleargram.internal;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import org.cleargram.api.NoiseAction;
import org.cleargram.spi.BlackListStoragePort;
import org.cleargram.spi.BlackListStorageRecord;
import org.cleargram.spi.BlackListStorageState;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class PersistentBlackListRepositoryTest {

    @Test
    public void initialLoadPublishesActionAndRulesTogether() {
        FakeStorage storage = new FakeStorage();
        PersistentBlackListRepository repository = new PersistentBlackListRepository(storage, new BlackListMatcher());
        assertFalse(repository.isReady());
        expectState(repository::getAction);
        storage.loaded(new BlackListStorageState(NoiseAction.HIDE, Arrays.asList(
                new BlackListStorageRecord("first", NoiseAction.HIDE, true),
                new BlackListStorageRecord("second", NoiseAction.COLLAPSE, false))));

        assertTrue(repository.isReady());
        assertEquals(NoiseAction.HIDE, repository.getAction());
        assertEquals("first", repository.snapshot().get(0).getCanonicalPattern());
        assertEquals(NoiseAction.COLLAPSE, repository.snapshot().get(1).getAction());
    }

    @Test
    public void invalidInitialStateFailsWithoutPublishingPartialRules() {
        FakeStorage storage = new FakeStorage();
        PersistentBlackListRepository repository = new PersistentBlackListRepository(storage, new BlackListMatcher());
        storage.loaded(new BlackListStorageState(NoiseAction.ALLOW,
                Collections.singletonList(new BlackListStorageRecord("first", NoiseAction.HIDE, true))));

        assertFalse(repository.isReady());
        expectState(repository::getAction);
        expectState(repository::snapshot);
    }

    @Test
    public void setActionIsDurableFirstAndPreservesRuleFieldsAndOrder() {
        FakeStorage storage = new FakeStorage();
        PersistentBlackListRepository repository = readyRepository(storage, NoiseAction.COLLAPSE);
        List<BlackListRule> before = repository.snapshot();
        storage.beforeUpdateAction = () -> {
            assertEquals(NoiseAction.COLLAPSE, repository.getAction());
            assertSame(before, repository.snapshot());
        };

        repository.setAction(NoiseAction.HIDE);

        assertEquals(NoiseAction.HIDE, repository.getAction());
        List<BlackListRule> rules = repository.snapshot();
        assertEquals("first", rules.get(0).getCanonicalPattern());
        assertTrue(rules.get(0).isEnabled());
        assertEquals("second", rules.get(1).getCanonicalPattern());
        assertFalse(rules.get(1).isEnabled());
        assertEquals(NoiseAction.HIDE, rules.get(0).getAction());
        assertEquals(NoiseAction.HIDE, rules.get(1).getAction());
    }

    @Test
    public void actionStorageFailureKeepsReadyActionAndSnapshot() {
        FakeStorage storage = new FakeStorage();
        PersistentBlackListRepository repository = readyRepository(storage, NoiseAction.HIDE);
        List<BlackListRule> before = repository.snapshot();
        storage.actionFailure = new IllegalStateException("storage failure");

        expectState(() -> repository.setAction(NoiseAction.COLLAPSE));

        assertTrue(repository.isReady());
        assertEquals(NoiseAction.HIDE, repository.getAction());
        assertSame(before, repository.snapshot());
    }

    @Test
    public void unsupportedActionDoesNotReachStorage() {
        FakeStorage storage = new FakeStorage();
        PersistentBlackListRepository repository = readyRepository(storage, NoiseAction.COLLAPSE);
        expectArgument(() -> repository.setAction(NoiseAction.ALLOW));
        assertEquals(0, storage.updateActionCalls);
    }

    private static PersistentBlackListRepository readyRepository(FakeStorage storage, NoiseAction action) {
        PersistentBlackListRepository repository = new PersistentBlackListRepository(storage, new BlackListMatcher());
        storage.loaded(new BlackListStorageState(action, Arrays.asList(
                new BlackListStorageRecord("first", NoiseAction.HIDE, true),
                new BlackListStorageRecord("second", NoiseAction.COLLAPSE, false))));
        return repository;
    }

    private static void expectState(Runnable runnable) {
        try { runnable.run(); fail("Expected IllegalStateException"); }
        catch (IllegalStateException expected) { }
    }

    private static void expectArgument(Runnable runnable) {
        try { runnable.run(); fail("Expected IllegalArgumentException"); }
        catch (IllegalArgumentException expected) { }
    }

    private static final class FakeStorage implements BlackListStoragePort {
        private LoadCallback callback;
        private int updateActionCalls;
        private Runnable beforeUpdateAction;
        private RuntimeException actionFailure;

        @Override public void loadState(LoadCallback callback) { this.callback = callback; }
        void loaded(BlackListStorageState state) { callback.onLoaded(state); }
        @Override public void insertRule(BlackListStorageRecord record) { }
        @Override public boolean deleteRule(String pattern) { return true; }
        @Override public boolean updateRuleEnabled(String pattern, boolean enabled) { return true; }
        @Override public void updateAction(NoiseAction action) {
            updateActionCalls++;
            if (beforeUpdateAction != null) beforeUpdateAction.run();
            if (actionFailure != null) throw actionFailure;
        }
    }
}

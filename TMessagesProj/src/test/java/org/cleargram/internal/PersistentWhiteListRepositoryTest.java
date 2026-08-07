package org.cleargram.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import org.cleargram.spi.WhiteListStoragePort;
import org.cleargram.spi.WhiteListStorageRecord;

public final class PersistentWhiteListRepositoryTest {

    @Test
    public void startsLoadingAndBecomesReadyAfterValidLoad() {
        FakeStorage storage = new FakeStorage();
        PersistentWhiteListRepository repository = new PersistentWhiteListRepository(storage, new WhiteListMatcher());

        assertFalse(repository.isReady());
        storage.completeLoad(Arrays.asList(
                new WhiteListStorageRecord("first", true),
                new WhiteListStorageRecord("second", false)));

        assertTrue(repository.isReady());
        assertEquals(Arrays.asList("first", "second"), patterns(repository.snapshot()));
        assertEquals(Collections.singletonList("first"), patterns(repository.enabledSnapshot()));
    }

    @Test
    public void nullLoadedListFailsWithoutPublishingSnapshot() {
        assertFailedLoad(null);
    }

    @Test
    public void nullLoadedRecordFailsWithoutPublishingSnapshot() {
        assertFailedLoad(Arrays.asList(new WhiteListStorageRecord("first", true), null));
    }

    @Test
    public void nonCanonicalAndWhitespaceOnlyPatternsFailWithoutPublishingSnapshot() {
        assertFailedLoad(Collections.singletonList(new WhiteListStorageRecord("Not Canonical", true)));
        assertFailedLoad(Collections.singletonList(new WhiteListStorageRecord(" \u2003 ", true)));
    }

    @Test
    public void overlongAndDuplicatePatternsFailWithoutPublishingSnapshot() {
        assertFailedLoad(Collections.singletonList(new WhiteListStorageRecord(repeat("a", 256), true)));
        assertFailedLoad(Arrays.asList(
                new WhiteListStorageRecord("duplicate", true),
                new WhiteListStorageRecord("duplicate", false)));
    }

    @Test
    public void loadFailureLeavesRepositoryNotReady() {
        FakeStorage storage = new FakeStorage();
        PersistentWhiteListRepository repository = new PersistentWhiteListRepository(storage, new WhiteListMatcher());

        storage.failLoad(new RuntimeException("load failure"));

        assertFalse(repository.isReady());
        expectAllRepositoryOperationsReject(repository);
    }

    @Test
    public void operationsBeforeReadyReject() {
        FakeStorage storage = new FakeStorage();
        PersistentWhiteListRepository repository = new PersistentWhiteListRepository(storage, new WhiteListMatcher());

        expectAllRepositoryOperationsReject(repository);
    }

    @Test
    public void successfulAddCallsStorageBeforePublishingAndAppendsRule() {
        FakeStorage storage = new FakeStorage();
        PersistentWhiteListRepository repository = readyRepository(storage, "first", true);
        storage.beforeInsert = () -> assertEquals(Collections.singletonList("first"), patterns(repository.snapshot()));

        repository.add(new WhiteListRule("second", false));

        assertEquals(1, storage.insertCalls);
        assertEquals("second", storage.insertedRecord.getCanonicalPattern());
        assertFalse(storage.insertedRecord.isEnabled());
        assertEquals(Arrays.asList("first", "second"), patterns(repository.snapshot()));
    }

    @Test
    public void successfulDeleteCallsStorageBeforePublishingAndPreservesRemainingOrder() {
        FakeStorage storage = new FakeStorage();
        PersistentWhiteListRepository repository = readyRepository(storage, "first", true, "second", false, "third", true);
        storage.beforeDelete = () -> assertEquals(Arrays.asList("first", "second", "third"), patterns(repository.snapshot()));

        assertTrue(repository.remove("second"));

        assertEquals(1, storage.deleteCalls);
        assertEquals(Arrays.asList("first", "third"), patterns(repository.snapshot()));
    }

    @Test
    public void successfulEnabledUpdateCallsStorageBeforePublishingAndPreservesPosition() {
        FakeStorage storage = new FakeStorage();
        PersistentWhiteListRepository repository = readyRepository(storage, "first", true, "second", false, "third", true);
        storage.beforeUpdate = () -> assertFalse(repository.snapshot().get(1).isEnabled());

        assertTrue(repository.setEnabled("second", true));

        assertEquals(1, storage.updateCalls);
        assertEquals(Arrays.asList("first", "second", "third"), patterns(repository.snapshot()));
        assertTrue(repository.snapshot().get(1).isEnabled());
    }

    @Test
    public void missingDeleteRowKeepsReadyAndSnapshotAndThrows() {
        FakeStorage storage = new FakeStorage();
        PersistentWhiteListRepository repository = readyRepository(storage, "first", true);
        List<WhiteListRule> publishedSnapshot = repository.snapshot();
        storage.deleteResult = false;

        expectIllegalState(() -> repository.remove("first"));

        assertTrue(repository.isReady());
        assertEquals(Collections.singletonList("first"), patterns(repository.snapshot()));
        assertEquals(Collections.singletonList("first"), patterns(publishedSnapshot));
    }

    @Test
    public void missingEnabledUpdateRowKeepsReadyAndSnapshotAndThrows() {
        FakeStorage storage = new FakeStorage();
        PersistentWhiteListRepository repository = readyRepository(storage, "first", true);
        List<WhiteListRule> publishedSnapshot = repository.snapshot();
        storage.updateResult = false;

        expectIllegalState(() -> repository.setEnabled("first", false));

        assertTrue(repository.isReady());
        assertTrue(repository.snapshot().get(0).isEnabled());
        assertTrue(publishedSnapshot.get(0).isEnabled());
    }

    @Test
    public void runtimeInsertFailureKeepsReadyAndSnapshotAndPropagates() {
        FakeStorage storage = new FakeStorage();
        PersistentWhiteListRepository repository = readyRepository(storage, "first", true);
        List<WhiteListRule> publishedSnapshot = repository.snapshot();
        storage.insertFailure = new RuntimeException("insert failure");

        expectRuntime(() -> repository.add(new WhiteListRule("second", true)));

        assertTrue(repository.isReady());
        assertEquals(Collections.singletonList("first"), patterns(repository.snapshot()));
        assertEquals(Collections.singletonList("first"), patterns(publishedSnapshot));
    }

    @Test
    public void runtimeDeleteFailureKeepsReadyAndSnapshotAndPropagates() {
        FakeStorage storage = new FakeStorage();
        PersistentWhiteListRepository repository = readyRepository(storage, "first", true);
        List<WhiteListRule> publishedSnapshot = repository.snapshot();
        storage.deleteFailure = new RuntimeException("delete failure");

        expectRuntime(() -> repository.remove("first"));

        assertTrue(repository.isReady());
        assertEquals(Collections.singletonList("first"), patterns(repository.snapshot()));
        assertEquals(Collections.singletonList("first"), patterns(publishedSnapshot));
    }

    @Test
    public void runtimeEnabledUpdateFailureKeepsReadyAndSnapshotAndPropagates() {
        FakeStorage storage = new FakeStorage();
        PersistentWhiteListRepository repository = readyRepository(storage, "first", true);
        List<WhiteListRule> publishedSnapshot = repository.snapshot();
        storage.updateFailure = new RuntimeException("update failure");

        expectRuntime(() -> repository.setEnabled("first", false));

        assertTrue(repository.isReady());
        assertTrue(repository.snapshot().get(0).isEnabled());
        assertTrue(publishedSnapshot.get(0).isEnabled());
    }

    @Test
    public void absentAndUnchangedOperationsDoNotCallStorage() {
        FakeStorage storage = new FakeStorage();
        PersistentWhiteListRepository repository = readyRepository(storage, "first", true);

        assertFalse(repository.remove("missing"));
        assertFalse(repository.setEnabled("missing", true));
        assertTrue(repository.setEnabled("first", true));

        assertEquals(0, storage.deleteCalls);
        assertEquals(0, storage.updateCalls);
    }

    @Test
    public void snapshotsAreImmutableAndPreviouslyPublishedSnapshotsDoNotChange() {
        FakeStorage storage = new FakeStorage();
        PersistentWhiteListRepository repository = readyRepository(storage, "first", true);
        List<WhiteListRule> initialSnapshot = repository.snapshot();
        List<WhiteListRule> initialEnabled = repository.enabledSnapshot();

        expectUnsupported(() -> initialSnapshot.clear());
        expectUnsupported(() -> initialEnabled.clear());
        repository.add(new WhiteListRule("second", true));
        List<WhiteListRule> afterAdd = repository.snapshot();
        repository.setEnabled("first", false);
        repository.remove("second");

        assertEquals(Collections.singletonList("first"), patterns(initialSnapshot));
        assertTrue(initialSnapshot.get(0).isEnabled());
        assertEquals(Arrays.asList("first", "second"), patterns(afterAdd));
        assertEquals(Collections.singletonList("first"), patterns(initialEnabled));
        assertFalse(repository.snapshot().get(0).isEnabled());
    }

    @Test
    public void snapshotReadsDoNotTriggerStorageOperations() {
        FakeStorage storage = new FakeStorage();
        PersistentWhiteListRepository repository = readyRepository(storage, "first", true);

        repository.snapshot();
        repository.enabledSnapshot();
        repository.snapshot();

        assertEquals(1, storage.loadCalls);
        assertEquals(0, storage.insertCalls);
        assertEquals(0, storage.deleteCalls);
        assertEquals(0, storage.updateCalls);
    }

    @Test
    public void inMemoryRepositoryIsAlwaysReady() {
        assertTrue(new InMemoryWhiteListRepository().isReady());
    }

    private void assertFailedLoad(List<WhiteListStorageRecord> records) {
        FakeStorage storage = new FakeStorage();
        PersistentWhiteListRepository repository = new PersistentWhiteListRepository(storage, new WhiteListMatcher());

        storage.completeLoad(records);

        assertFalse(repository.isReady());
        expectAllRepositoryOperationsReject(repository);
    }

    private PersistentWhiteListRepository readyRepository(FakeStorage storage, Object... values) {
        PersistentWhiteListRepository repository = new PersistentWhiteListRepository(storage, new WhiteListMatcher());
        List<WhiteListStorageRecord> records = new ArrayList<>();
        for (int index = 0; index < values.length; index += 2) {
            records.add(new WhiteListStorageRecord((String) values[index], (Boolean) values[index + 1]));
        }
        storage.completeLoad(records);
        return repository;
    }

    private List<String> patterns(List<WhiteListRule> rules) {
        List<String> patterns = new ArrayList<>();
        for (WhiteListRule rule : rules) {
            patterns.add(rule.getCanonicalPattern());
        }
        return patterns;
    }

    private void expectAllRepositoryOperationsReject(PersistentWhiteListRepository repository) {
        expectIllegalState(repository::snapshot);
        expectIllegalState(repository::enabledSnapshot);
        expectIllegalState(() -> repository.add(new WhiteListRule("new", true)));
        expectIllegalState(() -> repository.remove("new"));
        expectIllegalState(() -> repository.setEnabled("new", false));
    }

    private void expectIllegalState(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            // Expected.
        }
    }

    private void expectRuntime(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("expected RuntimeException");
        } catch (RuntimeException expected) {
            // Expected.
        }
    }

    private void expectUnsupported(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }

    private String repeat(String value, int count) {
        StringBuilder result = new StringBuilder(value.length() * count);
        for (int index = 0; index < count; index++) {
            result.append(value);
        }
        return result.toString();
    }

    private static final class FakeStorage implements WhiteListStoragePort {

        private LoadCallback callback;
        private int loadCalls;
        private int insertCalls;
        private int deleteCalls;
        private int updateCalls;
        private WhiteListStorageRecord insertedRecord;
        private boolean deleteResult = true;
        private boolean updateResult = true;
        private RuntimeException insertFailure;
        private RuntimeException deleteFailure;
        private RuntimeException updateFailure;
        private Runnable beforeInsert;
        private Runnable beforeDelete;
        private Runnable beforeUpdate;

        @Override
        public void loadRules(LoadCallback callback) {
            loadCalls++;
            this.callback = callback;
        }

        private void completeLoad(List<WhiteListStorageRecord> records) {
            callback.onLoaded(records);
        }

        private void failLoad(Throwable error) {
            callback.onFailed(error);
        }

        @Override
        public void insertRule(WhiteListStorageRecord record) {
            insertCalls++;
            if (beforeInsert != null) {
                beforeInsert.run();
            }
            if (insertFailure != null) {
                throw insertFailure;
            }
            insertedRecord = record;
        }

        @Override
        public boolean deleteRule(String canonicalPattern) {
            deleteCalls++;
            if (beforeDelete != null) {
                beforeDelete.run();
            }
            if (deleteFailure != null) {
                throw deleteFailure;
            }
            return deleteResult;
        }

        @Override
        public boolean updateRuleEnabled(String canonicalPattern, boolean enabled) {
            updateCalls++;
            if (beforeUpdate != null) {
                beforeUpdate.run();
            }
            if (updateFailure != null) {
                throw updateFailure;
            }
            return updateResult;
        }
    }
}

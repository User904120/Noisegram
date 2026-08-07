package org.cleargram.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

public final class InMemoryWhiteListRepositoryTest {

    @Test
    public void snapshotPreservesInsertionOrder() {
        InMemoryWhiteListRepository repository = repositoryWith(
                new WhiteListRule("first", true),
                new WhiteListRule("second", false),
                new WhiteListRule("third", true)
        );

        assertPatterns(repository.snapshot(), "first", "second", "third");
    }

    @Test
    public void enabledSnapshotFiltersAndPreservesOrder() {
        InMemoryWhiteListRepository repository = repositoryWith(
                new WhiteListRule("first", false),
                new WhiteListRule("second", true),
                new WhiteListRule("third", false),
                new WhiteListRule("fourth", true)
        );

        assertPatterns(repository.enabledSnapshot(), "second", "fourth");
    }

    @Test
    public void snapshotsAreImmutableAndDoNotChangeRepository() {
        InMemoryWhiteListRepository repository = repositoryWith(new WhiteListRule("first", true));
        List<WhiteListRule> snapshot = repository.snapshot();

        try {
            snapshot.clear();
            fail("snapshot must be immutable");
        } catch (UnsupportedOperationException expected) {
            // Expected immutable snapshot.
        }

        assertEquals(1, repository.snapshot().size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsDuplicateCanonicalPattern() {
        InMemoryWhiteListRepository repository = repositoryWith(new WhiteListRule("pattern", true));

        repository.add(new WhiteListRule("pattern", false));
    }

    @Test(expected = NullPointerException.class)
    public void rejectsNullRule() {
        new InMemoryWhiteListRepository().add(null);
    }

    @Test
    public void removesExistingAndReportsMissingPattern() {
        InMemoryWhiteListRepository repository = repositoryWith(new WhiteListRule("pattern", true));

        assertTrue(repository.remove("pattern"));
        assertFalse(repository.remove("missing"));
        assertTrue(repository.snapshot().isEmpty());
    }

    @Test
    public void setEnabledPreservesInsertionOrder() {
        InMemoryWhiteListRepository repository = repositoryWith(
                new WhiteListRule("first", true),
                new WhiteListRule("second", false)
        );

        assertTrue(repository.setEnabled("first", false));
        assertTrue(repository.setEnabled("second", true));
        assertPatterns(repository.snapshot(), "first", "second");
        assertFalse(repository.snapshot().get(0).isEnabled());
        assertTrue(repository.snapshot().get(1).isEnabled());
        assertTrue(repository.setEnabled("second", true));
        assertFalse(repository.setEnabled("missing", true));
    }

    @Test
    public void concurrentSnapshotsAndMutationsPreserveRepositoryInvariants() throws Exception {
        final InMemoryWhiteListRepository repository = repositoryWith(
                new WhiteListRule("first", true),
                new WhiteListRule("second", false)
        );
        final CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(4);
        List<Future<?>> futures = new ArrayList<>();

        futures.add(executor.submit(new Runnable() {
            @Override
            public void run() {
                await(start);
                for (int i = 0; i < 500; i++) {
                    repository.setEnabled("first", (i & 1) == 0);
                    repository.setEnabled("second", (i & 1) != 0);
                    String pattern = "temporary" + i;
                    repository.add(new WhiteListRule(pattern, true));
                    repository.remove(pattern);
                }
            }
        }));

        for (int reader = 0; reader < 3; reader++) {
            futures.add(executor.submit(new Runnable() {
                @Override
                public void run() {
                    await(start);
                    for (int i = 0; i < 1000; i++) {
                        assertSnapshotInvariants(repository.snapshot());
                        assertEnabledSnapshotInvariants(repository.enabledSnapshot());
                    }
                }
            }));
        }

        start.countDown();
        try {
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdown();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
        }
    }

    @Test(expected = NullPointerException.class)
    public void rejectsNullPattern() {
        new InMemoryWhiteListRepository().remove(null);
    }

    private static InMemoryWhiteListRepository repositoryWith(WhiteListRule... rules) {
        InMemoryWhiteListRepository repository = new InMemoryWhiteListRepository();
        for (WhiteListRule rule : rules) {
            repository.add(rule);
        }
        return repository;
    }

    private static void assertPatterns(List<WhiteListRule> rules, String... expectedPatterns) {
        assertEquals(expectedPatterns.length, rules.size());
        for (int i = 0; i < expectedPatterns.length; i++) {
            assertEquals(expectedPatterns[i], rules.get(i).getCanonicalPattern());
        }
    }

    private static void assertSnapshotInvariants(List<WhiteListRule> rules) {
        Set<String> patterns = new HashSet<>();
        for (WhiteListRule rule : rules) {
            assertTrue(rule != null);
            assertTrue(patterns.add(rule.getCanonicalPattern()));
        }
    }

    private static void assertEnabledSnapshotInvariants(List<WhiteListRule> rules) {
        for (WhiteListRule rule : rules) {
            assertTrue(rule != null);
            assertTrue(rule.isEnabled());
        }
    }

    private static void await(CountDownLatch start) {
        try {
            start.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }
}

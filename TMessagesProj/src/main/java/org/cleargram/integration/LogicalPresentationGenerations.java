package org.cleargram.integration;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Per-logical-presentation generation ownership for asynchronous work. */
final class LogicalPresentationGenerations {

    private final Map<LogicalPresentationId, Long> generations = new HashMap<>();

    long next(LogicalPresentationId logicalPresentationId) {
        Objects.requireNonNull(logicalPresentationId, "logicalPresentationId");
        long nextGeneration = current(logicalPresentationId) + 1L;
        generations.put(logicalPresentationId, nextGeneration);
        return nextGeneration;
    }

    long invalidate(LogicalPresentationId logicalPresentationId) {
        return next(logicalPresentationId);
    }

    long remove(LogicalPresentationId logicalPresentationId) {
        return next(logicalPresentationId);
    }

    boolean isCurrent(LogicalPresentationId logicalPresentationId, long generation) {
        Objects.requireNonNull(logicalPresentationId, "logicalPresentationId");
        return current(logicalPresentationId) == generation;
    }

    private long current(LogicalPresentationId logicalPresentationId) {
        Long generation = generations.get(logicalPresentationId);
        return generation != null ? generation : 0L;
    }
}

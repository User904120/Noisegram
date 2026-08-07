package org.cleargram.integration;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LogicalPresentationGenerationsTest {

    @Test
    public void generationsAreIndependentForTwoLogicalMessages() {
        LogicalPresentationGenerations generations = new LogicalPresentationGenerations();
        LogicalPresentationId first = single(1L);
        LogicalPresentationId second = grouped(7L);

        assertTrue(generations.isCurrent(first, generations.next(first)));
        assertTrue(generations.isCurrent(second, generations.next(second)));

        generations.invalidate(first);

        assertFalse(generations.isCurrent(first, 1L));
        assertTrue(generations.isCurrent(first, 2L));
        assertTrue(generations.isCurrent(second, 1L));
    }

    @Test
    public void staleGenerationIsSuppressedOnlyWithinItsLogicalPresentation() {
        LogicalPresentationGenerations generations = new LogicalPresentationGenerations();
        LogicalPresentationId first = single(10L);
        LogicalPresentationId second = single(11L);

        long firstGeneration = generations.next(first);
        long secondGeneration = generations.next(second);
        generations.invalidate(first);

        assertFalse(generations.isCurrent(first, firstGeneration));
        assertTrue(generations.isCurrent(second, secondGeneration));
    }

    @Test
    public void removeDoesNotInvalidateAnotherLogicalPresentation() {
        LogicalPresentationGenerations generations = new LogicalPresentationGenerations();
        LogicalPresentationId removed = grouped(20L);
        LogicalPresentationId retained = grouped(21L);

        generations.next(removed);
        generations.next(retained);
        generations.remove(removed);

        assertFalse(generations.isCurrent(removed, 1L));
        assertTrue(generations.isCurrent(removed, 2L));
        assertTrue(generations.isCurrent(retained, 1L));
    }

    @Test
    public void equalGenerationValuesRemainDistinctAcrossLogicalPresentationIds() {
        LogicalPresentationGenerations generations = new LogicalPresentationGenerations();
        LogicalPresentationId single = single(30L);
        LogicalPresentationId grouped = grouped(30L);

        assertTrue(generations.isCurrent(single, generations.next(single)));
        assertTrue(generations.isCurrent(grouped, generations.next(grouped)));
        assertTrue(generations.isCurrent(single, 1L));
        assertTrue(generations.isCurrent(grouped, 1L));
    }

    private static LogicalPresentationId single(long messageId) {
        return LogicalPresentationId.singleMessage(1, 100L, 0L, messageId);
    }

    private static LogicalPresentationId grouped(long groupId) {
        return LogicalPresentationId.groupedMessage(1, 100L, 0L, groupId);
    }
}

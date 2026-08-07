package org.cleargram.integration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.cleargram.api.DuplicateVideoResult;

/** Immutable presentation-only result derived from an ordered coordinator snapshot. */
final class DuplicateVideoCoordinatorResult {

    private final LogicalPresentationId logicalPresentationId;
    private final long generation;
    private final List<Long> presentationItemIds;
    private final List<Long> hiddenPresentationItemIds;
    private final boolean hideWholeMessage;

    DuplicateVideoCoordinatorResult(
            LogicalPresentationId logicalPresentationId,
            long generation,
            List<Long> presentationItemIds,
            List<Long> hiddenPresentationItemIds,
            boolean hideWholeMessage
    ) {
        this.logicalPresentationId = Objects.requireNonNull(
                logicalPresentationId, "logicalPresentationId");
        this.generation = generation;
        Objects.requireNonNull(presentationItemIds, "presentationItemIds");
        Objects.requireNonNull(hiddenPresentationItemIds, "hiddenPresentationItemIds");
        Set<Long> availableIds = new HashSet<>();
        List<Long> copiedPresentationIds = new ArrayList<>(presentationItemIds.size());
        for (Long presentationItemId : presentationItemIds) {
            Objects.requireNonNull(presentationItemId, "presentationItemIds must not contain null");
            if (!availableIds.add(presentationItemId)) {
                throw new IllegalArgumentException("duplicate presentationItemId");
            }
            copiedPresentationIds.add(presentationItemId);
        }
        Set<Long> seenHiddenIds = new HashSet<>();
        List<Long> copiedHiddenIds = new ArrayList<>(hiddenPresentationItemIds.size());
        for (Long hiddenPresentationItemId : hiddenPresentationItemIds) {
            Objects.requireNonNull(hiddenPresentationItemId,
                    "hiddenPresentationItemIds must not contain null");
            if (!availableIds.contains(hiddenPresentationItemId)) {
                throw new IllegalArgumentException("hidden item is not present in the snapshot");
            }
            if (!seenHiddenIds.add(hiddenPresentationItemId)) {
                throw new IllegalArgumentException("duplicate hiddenPresentationItemId");
            }
            copiedHiddenIds.add(hiddenPresentationItemId);
        }
        this.presentationItemIds = Collections.unmodifiableList(copiedPresentationIds);
        this.hiddenPresentationItemIds = Collections.unmodifiableList(copiedHiddenIds);
        this.hideWholeMessage = hideWholeMessage;
    }

    static DuplicateVideoCoordinatorResult fromClassification(
            LogicalPresentationId logicalPresentationId,
            long generation,
            DuplicateVideoCoordinatorSnapshot snapshot,
            DuplicateVideoResult classification
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(classification, "classification");

        Set<Long> hiddenIds = new HashSet<>(classification.getHiddenItemIds());
        List<Long> orderedPresentationIds = new ArrayList<>(snapshot.getItems().size());
        List<Long> orderedIds = new ArrayList<>(hiddenIds.size());
        for (DuplicateVideoCoordinatorSnapshot.Item item : snapshot.getItems()) {
            orderedPresentationIds.add(item.getPresentationItemId());
            if (hiddenIds.remove(item.getPresentationItemId())) {
                orderedIds.add(item.getPresentationItemId());
            }
        }
        if (!hiddenIds.isEmpty()) {
            throw new IllegalArgumentException("classification contains an unknown presentationItemId");
        }
        return new DuplicateVideoCoordinatorResult(
                logicalPresentationId, generation, orderedPresentationIds, orderedIds,
                classification.shouldHideWholeMessage());
    }

    long getGeneration() {
        return generation;
    }

    LogicalPresentationId getLogicalPresentationId() {
        return logicalPresentationId;
    }

    List<Long> getPresentationItemIds() {
        return presentationItemIds;
    }

    List<Long> getHiddenPresentationItemIds() {
        return hiddenPresentationItemIds;
    }

    boolean shouldHideWholeMessage() {
        return hideWholeMessage;
    }
}

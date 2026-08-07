package org.cleargram.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.cleargram.api.DuplicateVideoMatchMode;
import org.cleargram.api.DuplicateVideoResult;
import org.cleargram.api.DuplicateVideoRuntimeStatus;
import org.cleargram.api.DuplicateVideoStateSnapshot;
import org.cleargram.api.NoiseCore;
import org.cleargram.internal.DuplicateVideoHashingComponent;
import org.cleargram.internal.DuplicateVideoHashingItem;
import org.cleargram.internal.DuplicateVideoHashingRequest;
import org.cleargram.internal.DuplicateVideoHashingResult;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;

/**
 * Chat-scoped owner of Duplicate Video work generation and queue handoff.
 *
 * <p>Telegram objects must be converted to {@link DuplicateVideoCoordinatorSnapshot}
 * before submission. The worker queue resolves local files and hashes only; Core is
 * called exclusively from the owning Cleargram storage queue.</p>
 */
final class DuplicateVideoCoordinator {

    private static final int KEY_VERSION = 1;

    private final NoiseCore core;
    private final DispatchQueue workerQueue;
    private final DispatchQueue storageQueue;
    private final TelegramDuplicateVideoSourceResolver sourceResolver;
    private final DuplicateVideoHashingComponent hashingComponent;
    private final DuplicateVideoPresentationConsumer presentationConsumer;

    private final LogicalPresentationGenerations generations = new LogicalPresentationGenerations();
    private boolean closed;

    DuplicateVideoCoordinator(
            NoiseCore core,
            DispatchQueue workerQueue,
            DispatchQueue storageQueue,
            DuplicateVideoPresentationConsumer presentationConsumer
    ) {
        this(core, workerQueue, storageQueue, new TelegramDuplicateVideoSourceResolver(),
                new DuplicateVideoHashingComponent(), presentationConsumer);
    }

    DuplicateVideoCoordinator(
            NoiseCore core,
            DispatchQueue workerQueue,
            DispatchQueue storageQueue,
            TelegramDuplicateVideoSourceResolver sourceResolver,
            DuplicateVideoHashingComponent hashingComponent,
            DuplicateVideoPresentationConsumer presentationConsumer
    ) {
        this.core = Objects.requireNonNull(core, "core");
        this.workerQueue = Objects.requireNonNull(workerQueue, "workerQueue");
        this.storageQueue = Objects.requireNonNull(storageQueue, "storageQueue");
        this.sourceResolver = Objects.requireNonNull(sourceResolver, "sourceResolver");
        this.hashingComponent = Objects.requireNonNull(hashingComponent, "hashingComponent");
        this.presentationConsumer = Objects.requireNonNull(presentationConsumer, "presentationConsumer");
    }

    synchronized long submit(
            LogicalPresentationId logicalPresentationId,
            DuplicateVideoCoordinatorSnapshot snapshot
    ) {
        Objects.requireNonNull(logicalPresentationId, "logicalPresentationId");
        Objects.requireNonNull(snapshot, "snapshot");
        long workGeneration = generations.next(logicalPresentationId);
        if (closed) {
            return workGeneration;
        }
        WorkItem workItem = new WorkItem(logicalPresentationId, workGeneration, snapshot);
        postStorage(() -> beginOnStorage(workItem));
        return workGeneration;
    }

    synchronized long invalidate(LogicalPresentationId logicalPresentationId) {
        return generations.invalidate(Objects.requireNonNull(
                logicalPresentationId, "logicalPresentationId"));
    }

    synchronized long remove(LogicalPresentationId logicalPresentationId) {
        return generations.remove(Objects.requireNonNull(
                logicalPresentationId, "logicalPresentationId"));
    }

    synchronized void close() {
        closed = true;
    }

    private void beginOnStorage(WorkItem workItem) {
        if (!isCurrent(workItem)) {
            return;
        }
        final DuplicateVideoStateSnapshot state;
        try {
            state = core.getDuplicateVideoState();
        } catch (Throwable throwable) {
            failOpen(throwable);
            return;
        }
        if (state.getStatus() != DuplicateVideoRuntimeStatus.READY) {
            return;
        }
        DuplicateVideoMatchMode mode = state.getMatchMode();
        if (mode == null) {
            return;
        }
        try {
            workerQueue.postRunnable(() -> hashOnWorker(workItem, mode));
        } catch (Throwable throwable) {
            failOpen(throwable);
        }
    }

    private void hashOnWorker(WorkItem workItem, DuplicateVideoMatchMode mode) {
        if (!isCurrent(workItem)) {
            return;
        }
        final DuplicateVideoHashingResult hashingResult;
        try {
            List<DuplicateVideoHashingItem> resolvedItems = new ArrayList<>();
            boolean hasUnavailableItem = false;
            for (DuplicateVideoCoordinatorSnapshot.Item item : workItem.snapshot.getItems()) {
                TelegramDuplicateVideoSourceResolution resolution =
                        sourceResolver.resolve(item.getMediaSource());
                if (resolution.getStatus()
                        != TelegramDuplicateVideoSourceResolution.Status.LOCAL_AVAILABLE) {
                    hasUnavailableItem = true;
                    continue;
                }
                resolvedItems.add(new DuplicateVideoHashingItem(
                        item.getPresentationItemId(), resolution.getSourceOrNull()));
            }
            if (resolvedItems.isEmpty()) {
                return;
            }
            DuplicateVideoHashingRequest request = new DuplicateVideoHashingRequest(
                    mode,
                    KEY_VERSION,
                    resolvedItems,
                    workItem.snapshot.getLogicalText(),
                    workItem.snapshot.hasOtherVisibleMedia() || hasUnavailableItem);
            hashingResult = hashingComponent.hash(request);
        } catch (Throwable throwable) {
            postStorage(() -> failOpen(throwable));
            return;
        }
        postStorage(() -> classifyOnStorage(workItem, mode, hashingResult));
    }

    private void classifyOnStorage(
            WorkItem workItem,
            DuplicateVideoMatchMode expectedMode,
            DuplicateVideoHashingResult hashingResult
    ) {
        if (!isCurrent(workItem)) {
            return;
        }
        try {
            DuplicateVideoStateSnapshot state = core.getDuplicateVideoState();
            if (state.getStatus() != DuplicateVideoRuntimeStatus.READY
                    || state.getMatchMode() != expectedMode || !hashingResult.hasBatch()) {
                return;
            }
            DuplicateVideoResult result = core.classifyDuplicateVideo(hashingResult.getBatchOrNull());
            if (isCurrent(workItem)) {
                publish(workItem, DuplicateVideoCoordinatorResult.fromClassification(
                        workItem.logicalPresentationId, workItem.generation, workItem.snapshot, result));
            }
        } catch (Throwable throwable) {
            failOpen(throwable);
        }
    }

    private void failOpen(Throwable throwable) {
        if (throwable != null) {
            FileLog.e(throwable);
        }
    }

    private synchronized void publish(WorkItem workItem, DuplicateVideoCoordinatorResult result) {
        if (closed || !generations.isCurrent(
                workItem.logicalPresentationId, workItem.generation)) {
            return;
        }
        try {
            presentationConsumer.onDuplicateVideoPresentation(result);
        } catch (Throwable throwable) {
            FileLog.e(throwable);
        }
    }

    private void postStorage(Runnable runnable) {
        try {
            storageQueue.postRunnable(runnable);
        } catch (Throwable throwable) {
            FileLog.e(throwable);
        }
    }

    private synchronized boolean isCurrent(WorkItem workItem) {
        return !closed && generations.isCurrent(
                workItem.logicalPresentationId, workItem.generation);
    }

    private static final class WorkItem {

        private final LogicalPresentationId logicalPresentationId;
        private final long generation;
        private final DuplicateVideoCoordinatorSnapshot snapshot;

        private WorkItem(
                LogicalPresentationId logicalPresentationId,
                long generation,
                DuplicateVideoCoordinatorSnapshot snapshot
        ) {
            this.logicalPresentationId = logicalPresentationId;
            this.generation = generation;
            this.snapshot = snapshot;
        }
    }
}

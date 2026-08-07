package org.cleargram.integration;

import java.util.HashMap;
import java.util.Map;

import org.telegram.messenger.MessageObject;

/** Chat-scoped Duplicate Video lifecycle bridge; all work is delegated to existing boundaries. */
public final class TelegramDuplicateVideoChatRuntime {

    private final int accountId;
    private final DuplicateVideoCoordinator coordinator;
    private final TelegramDuplicateVideoPresentationApplier presentationApplier;
    private final TelegramDuplicateVideoSnapshotFactory snapshotFactory = new TelegramDuplicateVideoSnapshotFactory();
    private final Map<LogicalPresentationId, DuplicateVideoCoordinatorSnapshot> submittedSnapshots = new HashMap<>();
    private boolean closed;

    TelegramDuplicateVideoChatRuntime(int accountId, DuplicateVideoCoordinator coordinator,
            TelegramDuplicateVideoPresentationApplier presentationApplier) {
        this.accountId = accountId;
        this.coordinator = coordinator;
        this.presentationApplier = presentationApplier;
    }

    public void onBound(MessageObject message, MessageObject.GroupedMessages groupedMessages,
            TelegramDecisionContext context, boolean continueDuplicateVideo) {
        if (closed || !continueDuplicateVideo) return;
        TelegramDuplicateVideoSnapshotFactory.Snapshot snapshot = snapshotFactory.create(
                accountId, message, groupedMessages);
        if (snapshot == null) return;
        LogicalPresentationId logicalId = snapshot.getLogicalPresentationId();
        presentationApplier.registerPresentationItem(logicalId, message.getId(), context);
        DuplicateVideoCoordinatorSnapshot previous = submittedSnapshots.get(logicalId);
        if (previous != null && sameSnapshot(previous, snapshot.getCoordinatorSnapshot())) return;
        if (previous != null) {
            unregisterObsoleteItems(logicalId, previous, snapshot.getCoordinatorSnapshot());
            long invalidationGeneration = coordinator.invalidate(logicalId);
            presentationApplier.clearLogicalPresentation(logicalId, invalidationGeneration);
            presentationApplier.registerPresentationItem(logicalId, message.getId(), context);
        }
        submittedSnapshots.put(logicalId, snapshot.getCoordinatorSnapshot());
        coordinator.submit(logicalId, snapshot.getCoordinatorSnapshot());
    }

    public void onRecycled(LogicalPresentationId logicalId, long presentationItemId) {
        if (!closed) presentationApplier.unregisterPresentationItem(logicalId, presentationItemId);
    }

    public void onRecycled(MessageObject message, MessageObject.GroupedMessages groupedMessages) {
        if (closed) return;
        TelegramDuplicateVideoSnapshotFactory.Snapshot snapshot = snapshotFactory.create(
                accountId, message, groupedMessages);
        if (snapshot != null) {
            onRecycled(snapshot.getLogicalPresentationId(), message.getId());
        }
    }

    public void remove(LogicalPresentationId logicalId) {
        if (closed) return;
        submittedSnapshots.remove(logicalId);
        long removalGeneration = coordinator.remove(logicalId);
        presentationApplier.clearLogicalPresentation(logicalId, removalGeneration);
    }

    public void close() {
        if (closed) return;
        closed = true;
        submittedSnapshots.clear();
        coordinator.close();
        presentationApplier.close();
    }

    private static boolean sameSnapshot(DuplicateVideoCoordinatorSnapshot first,
            DuplicateVideoCoordinatorSnapshot second) {
        if (!first.getLogicalText().equals(second.getLogicalText())
                || first.hasOtherVisibleMedia() != second.hasOtherVisibleMedia()
                || first.getItems().size() != second.getItems().size()) return false;
        for (int i = 0; i < first.getItems().size(); i++) {
            if (first.getItems().get(i).getPresentationItemId()
                    != second.getItems().get(i).getPresentationItemId()) return false;
        }
        return true;
    }

    private void unregisterObsoleteItems(
            LogicalPresentationId logicalId,
            DuplicateVideoCoordinatorSnapshot previous,
            DuplicateVideoCoordinatorSnapshot current
    ) {
        for (DuplicateVideoCoordinatorSnapshot.Item previousItem : previous.getItems()) {
            if (!containsPresentationItem(current, previousItem.getPresentationItemId())) {
                presentationApplier.unregisterPresentationItem(
                        logicalId, previousItem.getPresentationItemId());
            }
        }
    }

    private static boolean containsPresentationItem(
            DuplicateVideoCoordinatorSnapshot snapshot,
            long presentationItemId
    ) {
        for (DuplicateVideoCoordinatorSnapshot.Item item : snapshot.getItems()) {
            if (item.getPresentationItemId() == presentationItemId) {
                return true;
            }
        }
        return false;
    }
}

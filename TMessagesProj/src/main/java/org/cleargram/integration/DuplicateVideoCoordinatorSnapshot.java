package org.cleargram.integration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable ordered Telegram integration input for one logical message. */
final class DuplicateVideoCoordinatorSnapshot {

    static final class Item {

        private final long presentationItemId;
        private final TelegramDuplicateVideoMediaSourceDescriptor mediaSource;
        private final int orderedPosition;

        Item(
                long presentationItemId,
                TelegramDuplicateVideoMediaSourceDescriptor mediaSource,
                int orderedPosition
        ) {
            this.presentationItemId = presentationItemId;
            this.mediaSource = Objects.requireNonNull(mediaSource, "mediaSource");
            if (orderedPosition < 0) {
                throw new IllegalArgumentException("orderedPosition must not be negative");
            }
            this.orderedPosition = orderedPosition;
        }

        long getPresentationItemId() {
            return presentationItemId;
        }

        TelegramDuplicateVideoMediaSourceDescriptor getMediaSource() {
            return mediaSource;
        }

        int getOrderedPosition() {
            return orderedPosition;
        }
    }

    private final List<Item> items;
    private final String logicalText;
    private final boolean hasOtherVisibleMedia;

    DuplicateVideoCoordinatorSnapshot(
            List<Item> items,
            String logicalText,
            boolean hasOtherVisibleMedia
    ) {
        Objects.requireNonNull(items, "items");
        if (items.isEmpty()) {
            throw new IllegalArgumentException("items must not be empty");
        }
        List<Item> copiedItems = new ArrayList<>(items.size());
        Set<Long> itemIds = new HashSet<>();
        for (int position = 0; position < items.size(); position++) {
            Item item = Objects.requireNonNull(items.get(position), "items must not contain null");
            if (item.getOrderedPosition() != position) {
                throw new IllegalArgumentException("items must use contiguous ordered positions");
            }
            if (!itemIds.add(item.getPresentationItemId())) {
                throw new IllegalArgumentException("duplicate presentationItemId");
            }
            copiedItems.add(item);
        }
        this.items = Collections.unmodifiableList(copiedItems);
        this.logicalText = logicalText == null ? "" : logicalText;
        this.hasOtherVisibleMedia = hasOtherVisibleMedia;
    }

    List<Item> getItems() {
        return items;
    }

    String getLogicalText() {
        return logicalText;
    }

    boolean hasOtherVisibleMedia() {
        return hasOtherVisibleMedia;
    }
}

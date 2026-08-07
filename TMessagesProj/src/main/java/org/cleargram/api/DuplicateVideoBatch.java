package org.cleargram.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable ordered batch of Duplicate Video items. */
public final class DuplicateVideoBatch {

    private static final int SUPPORTED_KEY_VERSION = 1;

    private final DuplicateVideoMatchMode matchMode;
    private final int keyVersion;
    private final List<DuplicateVideoItem> items;
    private final boolean hasOtherVisibleMedia;

    public DuplicateVideoBatch(
            DuplicateVideoMatchMode matchMode,
            int keyVersion,
            List<DuplicateVideoItem> items,
            boolean hasOtherVisibleMedia
    ) {
        this.matchMode = Objects.requireNonNull(matchMode, "matchMode");
        if (keyVersion <= 0) {
            throw new IllegalArgumentException("keyVersion must be positive");
        }
        if (keyVersion != SUPPORTED_KEY_VERSION) {
            throw new IllegalArgumentException("unsupported keyVersion");
        }
        Objects.requireNonNull(items, "items");
        if (items.isEmpty()) {
            throw new IllegalArgumentException("items must not be empty");
        }

        List<DuplicateVideoItem> copiedItems = new ArrayList<>(items.size());
        Set<Long> presentationItemIds = new HashSet<>();
        for (DuplicateVideoItem item : items) {
            Objects.requireNonNull(item, "items must not contain null");
            if (!presentationItemIds.add(item.getPresentationItemId())) {
                throw new IllegalArgumentException("duplicate presentationItemId");
            }
            copiedItems.add(item);
        }
        this.keyVersion = keyVersion;
        this.items = Collections.unmodifiableList(copiedItems);
        this.hasOtherVisibleMedia = hasOtherVisibleMedia;
    }

    public DuplicateVideoMatchMode getMatchMode() {
        return matchMode;
    }

    public int getKeyVersion() {
        return keyVersion;
    }

    public List<DuplicateVideoItem> getItems() {
        return items;
    }

    public boolean hasOtherVisibleMedia() {
        return hasOtherVisibleMedia;
    }
}

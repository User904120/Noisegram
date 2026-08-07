package org.cleargram.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.cleargram.api.DuplicateVideoMatchMode;

/** Immutable platform-neutral input for one synchronous hashing pass. */
public final class DuplicateVideoHashingRequest {

    private static final int SUPPORTED_KEY_VERSION = 1;

    private final DuplicateVideoMatchMode matchMode;
    private final int keyVersion;
    private final List<DuplicateVideoHashingItem> items;
    private final String logicalText;
    private final boolean hasOtherVisibleMedia;

    public DuplicateVideoHashingRequest(
            DuplicateVideoMatchMode matchMode,
            int keyVersion,
            List<DuplicateVideoHashingItem> items,
            String logicalText,
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
        List<DuplicateVideoHashingItem> copiedItems = new ArrayList<>(items.size());
        Set<Long> ids = new HashSet<>();
        for (DuplicateVideoHashingItem item : items) {
            Objects.requireNonNull(item, "items must not contain null");
            if (!ids.add(item.getPresentationItemId())) {
                throw new IllegalArgumentException("duplicate presentationItemId");
            }
            copiedItems.add(item);
        }
        this.keyVersion = keyVersion;
        this.items = Collections.unmodifiableList(copiedItems);
        this.logicalText = logicalText == null ? "" : logicalText;
        this.hasOtherVisibleMedia = hasOtherVisibleMedia;
    }

    public DuplicateVideoMatchMode getMatchMode() { return matchMode; }
    public int getKeyVersion() { return keyVersion; }
    public List<DuplicateVideoHashingItem> getItems() { return items; }
    public String getLogicalText() { return logicalText; }
    public boolean hasOtherVisibleMedia() { return hasOtherVisibleMedia; }
}

package org.cleargram.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable Core-owned Duplicate Video presentation result. */
public final class DuplicateVideoResult {

    private final List<Long> hiddenItemIds;
    private final boolean hideWholeMessage;

    DuplicateVideoResult(List<Long> hiddenItemIds, boolean hideWholeMessage) {
        Objects.requireNonNull(hiddenItemIds, "hiddenItemIds");
        List<Long> copiedIds = new ArrayList<>(hiddenItemIds.size());
        Set<Long> seenIds = new HashSet<>();
        for (Long hiddenItemId : hiddenItemIds) {
            Objects.requireNonNull(hiddenItemId, "hiddenItemIds must not contain null");
            if (!seenIds.add(hiddenItemId)) {
                throw new IllegalArgumentException("duplicate hiddenItemId");
            }
            copiedIds.add(hiddenItemId);
        }
        this.hiddenItemIds = Collections.unmodifiableList(copiedIds);
        this.hideWholeMessage = hideWholeMessage;
    }

    public List<Long> getHiddenItemIds() {
        return hiddenItemIds;
    }

    public boolean shouldHideWholeMessage() {
        return hideWholeMessage;
    }
}

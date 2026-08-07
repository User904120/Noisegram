package org.cleargram.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Internal bridge result read by NoiseCore. */
public final class DuplicateVideoManagerResult {

    private final List<Long> hiddenItemIds;
    private final boolean hideWholeMessage;

    DuplicateVideoManagerResult(List<Long> hiddenItemIds, boolean hideWholeMessage) {
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

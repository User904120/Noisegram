package org.cleargram.internal;

import java.util.List;

/**
 * Internal contract for storing White List rules.
 */
interface WhiteListRepository {

    boolean isReady();

    List<WhiteListRule> snapshot();

    List<WhiteListRule> enabledSnapshot();

    void add(WhiteListRule rule);

    boolean remove(String canonicalPattern);

    boolean setEnabled(String canonicalPattern, boolean enabled);
}

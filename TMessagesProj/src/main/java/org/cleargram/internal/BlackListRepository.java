package org.cleargram.internal;

import java.util.List;

import org.cleargram.api.NoiseAction;

/** Internal contract for storing Black List rules. */
interface BlackListRepository {

    boolean isReady();

    NoiseAction getAction();

    void setAction(NoiseAction action);

    List<BlackListRule> snapshot();

    List<BlackListRule> enabledSnapshot();

    void add(BlackListRule rule);

    boolean remove(String canonicalPattern);

    boolean setEnabled(String canonicalPattern, boolean enabled);
}

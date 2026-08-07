package org.cleargram.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.cleargram.spi.WhiteListStoragePort;
import org.cleargram.spi.WhiteListStorageRecord;

/** Persistent repository boundary backed by a platform-neutral storage port. */
final class PersistentWhiteListRepository implements WhiteListRepository {

    private enum State {
        LOADING,
        READY,
        FAILED
    }

    private final WhiteListStoragePort storagePort;
    private final WhiteListMatcher matcher;
    private volatile State state = State.LOADING;
    private volatile List<WhiteListRule> rules = Collections.emptyList();

    PersistentWhiteListRepository(WhiteListStoragePort storagePort, WhiteListMatcher matcher) {
        this.storagePort = Objects.requireNonNull(storagePort, "storagePort");
        this.matcher = Objects.requireNonNull(matcher, "matcher");
        try {
            storagePort.loadRules(new WhiteListStoragePort.LoadCallback() {
                @Override
                public void onLoaded(List<WhiteListStorageRecord> records) {
                    publishLoaded(records);
                }

                @Override
                public void onFailed(Throwable error) {
                    markFailed();
                }
            });
        } catch (RuntimeException error) {
            markFailed();
        }
    }

    @Override
    public boolean isReady() {
        return state == State.READY;
    }

    @Override
    public synchronized List<WhiteListRule> snapshot() {
        requireReady();
        return rules;
    }

    @Override
    public synchronized List<WhiteListRule> enabledSnapshot() {
        requireReady();
        List<WhiteListRule> enabledRules = new ArrayList<>();
        for (WhiteListRule rule : rules) {
            if (rule.isEnabled()) {
                enabledRules.add(rule);
            }
        }
        return Collections.unmodifiableList(enabledRules);
    }

    @Override
    public synchronized void add(WhiteListRule rule) {
        requireReady();
        Objects.requireNonNull(rule, "rule");
        String canonicalPattern = rule.getCanonicalPattern();
        for (WhiteListRule existingRule : rules) {
            if (existingRule.getCanonicalPattern().equals(canonicalPattern)) {
                throw new IllegalArgumentException("duplicate White List pattern");
            }
        }
        storagePort.insertRule(new WhiteListStorageRecord(canonicalPattern, rule.isEnabled()));
        List<WhiteListRule> updated = new ArrayList<>(rules);
        updated.add(rule);
        rules = immutable(updated);
    }

    @Override
    public synchronized boolean remove(String canonicalPattern) {
        requireReady();
        Objects.requireNonNull(canonicalPattern, "canonicalPattern");
        int index = indexOf(canonicalPattern);
        if (index < 0) {
            return false;
        }
        if (!storagePort.deleteRule(canonicalPattern)) {
            throw new IllegalStateException("storage row missing during delete");
        }
        List<WhiteListRule> updated = new ArrayList<>(rules);
        updated.remove(index);
        rules = immutable(updated);
        return true;
    }

    @Override
    public synchronized boolean setEnabled(String canonicalPattern, boolean enabled) {
        requireReady();
        Objects.requireNonNull(canonicalPattern, "canonicalPattern");
        int index = indexOf(canonicalPattern);
        if (index < 0) {
            return false;
        }
        WhiteListRule current = rules.get(index);
        if (current.isEnabled() == enabled) {
            return true;
        }
        if (!storagePort.updateRuleEnabled(canonicalPattern, enabled)) {
            throw new IllegalStateException("storage row missing during enabled update");
        }
        List<WhiteListRule> updated = new ArrayList<>(rules);
        updated.set(index, new WhiteListRule(canonicalPattern, enabled));
        rules = immutable(updated);
        return true;
    }

    private synchronized void publishLoaded(List<WhiteListStorageRecord> records) {
        if (records == null) {
            markFailed();
            return;
        }
        try {
            List<WhiteListRule> loaded = new ArrayList<>(records.size());
            for (WhiteListStorageRecord record : records) {
                if (record == null) {
                    throw new IllegalArgumentException("records must not contain null");
                }
                String pattern = record.getCanonicalPattern();
                if (!matcher.canonicalize(pattern).equals(pattern)) {
                    throw new IllegalArgumentException("storage pattern must be canonical");
                }
                for (WhiteListRule existingRule : loaded) {
                    if (existingRule.getCanonicalPattern().equals(pattern)) {
                        throw new IllegalArgumentException("duplicate White List pattern");
                    }
                }
                loaded.add(new WhiteListRule(pattern, record.isEnabled()));
            }
            rules = immutable(loaded);
            state = State.READY;
        } catch (RuntimeException error) {
            markFailed();
        }
    }

    private synchronized void markFailed() {
        state = State.FAILED;
    }

    private void requireReady() {
        if (state != State.READY) {
            throw new IllegalStateException("White List repository is not ready");
        }
    }

    private int indexOf(String canonicalPattern) {
        for (int index = 0; index < rules.size(); index++) {
            if (rules.get(index).getCanonicalPattern().equals(canonicalPattern)) {
                return index;
            }
        }
        return -1;
    }

    private List<WhiteListRule> immutable(List<WhiteListRule> source) {
        return Collections.unmodifiableList(new ArrayList<>(source));
    }
}

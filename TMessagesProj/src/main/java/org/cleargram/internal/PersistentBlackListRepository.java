package org.cleargram.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.cleargram.api.NoiseAction;
import org.cleargram.spi.BlackListStoragePort;
import org.cleargram.spi.BlackListStorageRecord;
import org.cleargram.spi.BlackListStorageState;

/** Persistent Black List repository backed by a platform-neutral storage port. */
final class PersistentBlackListRepository implements BlackListRepository {

    private enum State { LOADING, READY, FAILED }

    private final BlackListStoragePort storagePort;
    private final BlackListMatcher matcher;
    private volatile State state = State.LOADING;
    private volatile List<BlackListRule> rules = Collections.emptyList();
    private volatile NoiseAction action;

    PersistentBlackListRepository(BlackListStoragePort storagePort, BlackListMatcher matcher) {
        this.storagePort = Objects.requireNonNull(storagePort, "storagePort");
        this.matcher = Objects.requireNonNull(matcher, "matcher");
        try {
            storagePort.loadState(new BlackListStoragePort.LoadCallback() {
                @Override
                public void onLoaded(BlackListStorageState storageState) { publishLoaded(storageState); }

                @Override
                public void onFailed(Throwable error) { markFailed(); }
            });
        } catch (RuntimeException error) {
            markFailed();
        }
    }

    @Override
    public boolean isReady() { return state == State.READY; }

    @Override
    public synchronized NoiseAction getAction() {
        requireReady();
        return action;
    }

    @Override
    public synchronized void setAction(NoiseAction action) {
        requireReady();
        requireAction(action);
        if (this.action == action) {
            return;
        }
        storagePort.updateAction(action);
        List<BlackListRule> updated = new ArrayList<>(rules.size());
        for (BlackListRule rule : rules) {
            updated.add(new BlackListRule(rule.getCanonicalPattern(), action, rule.isEnabled()));
        }
        rules = immutable(updated);
        this.action = action;
    }

    @Override
    public synchronized List<BlackListRule> snapshot() { requireReady(); return rules; }

    @Override
    public synchronized List<BlackListRule> enabledSnapshot() {
        requireReady();
        List<BlackListRule> enabledRules = new ArrayList<>();
        for (BlackListRule rule : rules) {
            if (rule.isEnabled()) { enabledRules.add(rule); }
        }
        return Collections.unmodifiableList(enabledRules);
    }

    @Override
    public synchronized void add(BlackListRule rule) {
        requireReady();
        Objects.requireNonNull(rule, "rule");
        for (BlackListRule existing : rules) {
            if (existing.getCanonicalPattern().equals(rule.getCanonicalPattern())) {
                throw new IllegalArgumentException("duplicate Black List pattern");
            }
        }
        storagePort.insertRule(new BlackListStorageRecord(rule.getCanonicalPattern(), rule.getAction(), rule.isEnabled()));
        List<BlackListRule> updated = new ArrayList<>(rules);
        updated.add(rule);
        rules = immutable(updated);
    }

    @Override
    public synchronized boolean remove(String canonicalPattern) {
        requireReady();
        Objects.requireNonNull(canonicalPattern, "canonicalPattern");
        int index = indexOf(canonicalPattern);
        if (index < 0) { return false; }
        if (!storagePort.deleteRule(canonicalPattern)) {
            throw new IllegalStateException("storage row missing during delete");
        }
        List<BlackListRule> updated = new ArrayList<>(rules);
        updated.remove(index);
        rules = immutable(updated);
        return true;
    }

    @Override
    public synchronized boolean setEnabled(String canonicalPattern, boolean enabled) {
        requireReady();
        Objects.requireNonNull(canonicalPattern, "canonicalPattern");
        int index = indexOf(canonicalPattern);
        if (index < 0) { return false; }
        BlackListRule current = rules.get(index);
        if (current.isEnabled() == enabled) { return true; }
        if (!storagePort.updateRuleEnabled(canonicalPattern, enabled)) {
            throw new IllegalStateException("storage row missing during enabled update");
        }
        List<BlackListRule> updated = new ArrayList<>(rules);
        updated.set(index, new BlackListRule(canonicalPattern, current.getAction(), enabled));
        rules = immutable(updated);
        return true;
    }

    private synchronized void publishLoaded(BlackListStorageState storageState) {
        if (storageState == null) { markFailed(); return; }
        try {
            NoiseAction loadedAction = storageState.getAction();
            requireAction(loadedAction);
            List<BlackListStorageRecord> records = storageState.getRecords();
            if (records == null) { throw new IllegalArgumentException("records"); }
            List<BlackListRule> loaded = new ArrayList<>(records.size());
            for (BlackListStorageRecord record : records) {
                if (record == null) { throw new IllegalArgumentException("records must not contain null"); }
                String pattern = record.getCanonicalPattern();
                if (!matcher.canonicalize(pattern).equals(pattern)) {
                    throw new IllegalArgumentException("storage pattern must be canonical");
                }
                NoiseAction action = record.getAction();
                for (BlackListRule existing : loaded) {
                    if (existing.getCanonicalPattern().equals(pattern)) {
                        throw new IllegalArgumentException("duplicate Black List pattern");
                    }
                }
                loaded.add(new BlackListRule(pattern, action, record.isEnabled()));
            }
            List<BlackListRule> loadedSnapshot = immutable(loaded);
            action = loadedAction;
            rules = loadedSnapshot;
            state = State.READY;
        } catch (RuntimeException error) {
            markFailed();
        }
    }

    private synchronized void markFailed() { state = State.FAILED; }

    private void requireReady() {
        if (state != State.READY) { throw new IllegalStateException("Black List repository is not ready"); }
    }

    private int indexOf(String canonicalPattern) {
        for (int index = 0; index < rules.size(); index++) {
            if (rules.get(index).getCanonicalPattern().equals(canonicalPattern)) { return index; }
        }
        return -1;
    }

    private List<BlackListRule> immutable(List<BlackListRule> source) {
        return Collections.unmodifiableList(new ArrayList<>(source));
    }

    private static void requireAction(NoiseAction action) {
        if (action != NoiseAction.HIDE && action != NoiseAction.COLLAPSE) {
            throw new IllegalArgumentException("Black List action must be HIDE or COLLAPSE");
        }
    }
}

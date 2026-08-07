package org.cleargram.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Internal insertion-ordered in-memory White List repository.
 */
final class InMemoryWhiteListRepository implements WhiteListRepository {

    private final Map<String, WhiteListRule> rules = new LinkedHashMap<>();

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public synchronized List<WhiteListRule> snapshot() {
        return immutableSnapshot(rules.values());
    }

    @Override
    public synchronized List<WhiteListRule> enabledSnapshot() {
        List<WhiteListRule> enabledRules = new ArrayList<>();
        for (WhiteListRule rule : rules.values()) {
            if (rule.isEnabled()) {
                enabledRules.add(rule);
            }
        }
        return Collections.unmodifiableList(enabledRules);
    }

    @Override
    public synchronized void add(WhiteListRule rule) {
        Objects.requireNonNull(rule, "rule");
        String canonicalPattern = rule.getCanonicalPattern();
        if (rules.containsKey(canonicalPattern)) {
            throw new IllegalArgumentException("duplicate White List pattern");
        }
        rules.put(canonicalPattern, rule);
    }

    @Override
    public synchronized boolean remove(String canonicalPattern) {
        Objects.requireNonNull(canonicalPattern, "canonicalPattern");
        return rules.remove(canonicalPattern) != null;
    }

    @Override
    public synchronized boolean setEnabled(String canonicalPattern, boolean enabled) {
        Objects.requireNonNull(canonicalPattern, "canonicalPattern");
        WhiteListRule rule = rules.get(canonicalPattern);
        if (rule == null) {
            return false;
        }
        if (rule.isEnabled() != enabled) {
            rules.put(canonicalPattern, new WhiteListRule(canonicalPattern, enabled));
        }
        return true;
    }

    private List<WhiteListRule> immutableSnapshot(Iterable<WhiteListRule> source) {
        List<WhiteListRule> snapshot = new ArrayList<>();
        for (WhiteListRule rule : source) {
            snapshot.add(rule);
        }
        return Collections.unmodifiableList(snapshot);
    }
}

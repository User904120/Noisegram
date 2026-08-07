package org.cleargram.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.cleargram.api.NoiseAction;

/** Internal insertion-ordered in-memory Black List repository. */
final class InMemoryBlackListRepository implements BlackListRepository {

    private final Map<String, BlackListRule> rules = new LinkedHashMap<>();
    private NoiseAction action = NoiseAction.COLLAPSE;

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public synchronized NoiseAction getAction() {
        return action;
    }

    @Override
    public synchronized void setAction(NoiseAction action) {
        requireAction(action);
        if (this.action == action) {
            return;
        }
        Map<String, BlackListRule> updated = new LinkedHashMap<>();
        for (BlackListRule rule : rules.values()) {
            updated.put(rule.getCanonicalPattern(), new BlackListRule(
                    rule.getCanonicalPattern(), action, rule.isEnabled()));
        }
        rules.clear();
        rules.putAll(updated);
        this.action = action;
    }

    @Override
    public synchronized List<BlackListRule> snapshot() {
        return immutable(rules.values());
    }

    @Override
    public synchronized List<BlackListRule> enabledSnapshot() {
        List<BlackListRule> enabledRules = new ArrayList<>();
        for (BlackListRule rule : rules.values()) {
            if (rule.isEnabled()) {
                enabledRules.add(rule);
            }
        }
        return Collections.unmodifiableList(enabledRules);
    }

    @Override
    public synchronized void add(BlackListRule rule) {
        Objects.requireNonNull(rule, "rule");
        if (rules.containsKey(rule.getCanonicalPattern())) {
            throw new IllegalArgumentException("duplicate Black List pattern");
        }
        rules.put(rule.getCanonicalPattern(), rule);
    }

    @Override
    public synchronized boolean remove(String canonicalPattern) {
        Objects.requireNonNull(canonicalPattern, "canonicalPattern");
        return rules.remove(canonicalPattern) != null;
    }

    @Override
    public synchronized boolean setEnabled(String canonicalPattern, boolean enabled) {
        Objects.requireNonNull(canonicalPattern, "canonicalPattern");
        BlackListRule rule = rules.get(canonicalPattern);
        if (rule == null) {
            return false;
        }
        if (rule.isEnabled() != enabled) {
            rules.put(canonicalPattern, new BlackListRule(canonicalPattern, rule.getAction(), enabled));
        }
        return true;
    }

    private List<BlackListRule> immutable(Iterable<BlackListRule> source) {
        List<BlackListRule> snapshot = new ArrayList<>();
        for (BlackListRule rule : source) {
            snapshot.add(rule);
        }
        return Collections.unmodifiableList(snapshot);
    }

    private static void requireAction(NoiseAction action) {
        if (action != NoiseAction.HIDE && action != NoiseAction.COLLAPSE) {
            throw new IllegalArgumentException("Black List action must be HIDE or COLLAPSE");
        }
    }

}

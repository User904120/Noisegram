package org.cleargram.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.cleargram.api.WhiteListRuleSnapshot;
import org.cleargram.spi.WhiteListStoragePort;

/**
 * Internal administrative boundary for White List rules.
 */
public final class WhiteListRuleManager {

    private final WhiteListRepository repository;
    private final WhiteListMatcher matcher;
    private final WhiteListEvaluator evaluator;

    public static WhiteListRuleManager createEmpty() {
        return new WhiteListRuleManager(new InMemoryWhiteListRepository(), new WhiteListMatcher());
    }

    public static WhiteListRuleManager createPersistent(WhiteListStoragePort storagePort) {
        Objects.requireNonNull(storagePort, "storagePort");
        WhiteListMatcher matcher = new WhiteListMatcher();
        return new WhiteListRuleManager(new PersistentWhiteListRepository(storagePort, matcher), matcher);
    }

    WhiteListRuleManager(WhiteListRepository repository, WhiteListMatcher matcher) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.matcher = Objects.requireNonNull(matcher, "matcher");
        evaluator = new WhiteListEvaluator(this.repository, this.matcher);
    }

    public WhiteListEvaluator createEvaluator() {
        return evaluator;
    }

    public List<WhiteListRuleSnapshot> getWhiteListRules() {
        return snapshots(repository.snapshot());
    }

    public WhiteListRuleSnapshot addWhiteListRule(String pattern, boolean enabled) {
        WhiteListRule rule = matcher.createRule(pattern, enabled, repository.snapshot());
        repository.add(rule);
        return snapshot(rule);
    }

    public boolean removeWhiteListRule(String pattern) {
        return repository.remove(matcher.canonicalize(pattern));
    }

    public boolean setWhiteListRuleEnabled(String pattern, boolean enabled) {
        return repository.setEnabled(matcher.canonicalize(pattern), enabled);
    }

    private List<WhiteListRuleSnapshot> snapshots(List<WhiteListRule> rules) {
        List<WhiteListRuleSnapshot> snapshots = new ArrayList<>(rules.size());
        for (WhiteListRule rule : rules) {
            snapshots.add(snapshot(rule));
        }
        return Collections.unmodifiableList(snapshots);
    }

    private WhiteListRuleSnapshot snapshot(WhiteListRule rule) {
        return new WhiteListRuleSnapshot(rule.getCanonicalPattern(), rule.isEnabled());
    }
}

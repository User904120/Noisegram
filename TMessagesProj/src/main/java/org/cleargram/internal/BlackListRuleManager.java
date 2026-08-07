package org.cleargram.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.cleargram.api.BlackListRuleSnapshot;
import org.cleargram.api.NoiseAction;
import org.cleargram.spi.BlackListStoragePort;

/** Internal administrative boundary for Black List rules. */
public final class BlackListRuleManager {

    private final BlackListRepository repository;
    private final BlackListMatcher matcher;
    private final BlackListEvaluator evaluator;

    public static BlackListRuleManager createEmpty() {
        return new BlackListRuleManager(new InMemoryBlackListRepository(), new BlackListMatcher());
    }

    public static BlackListRuleManager createPersistent(BlackListStoragePort storagePort) {
        Objects.requireNonNull(storagePort, "storagePort");
        BlackListMatcher matcher = new BlackListMatcher();
        return new BlackListRuleManager(new PersistentBlackListRepository(storagePort, matcher), matcher);
    }

    BlackListRuleManager(BlackListRepository repository, BlackListMatcher matcher) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.matcher = Objects.requireNonNull(matcher, "matcher");
        evaluator = new BlackListEvaluator(this.repository, this.matcher);
    }

    public BlackListEvaluator createEvaluator() {
        return evaluator;
    }

    public List<BlackListRuleSnapshot> getBlackListRules() {
        List<BlackListRule> rules = repository.snapshot();
        List<BlackListRuleSnapshot> snapshots = new ArrayList<>(rules.size());
        for (BlackListRule rule : rules) {
            snapshots.add(snapshot(rule));
        }
        return Collections.unmodifiableList(snapshots);
    }

    public NoiseAction getBlackListAction() {
        return repository.getAction();
    }

    public void setBlackListAction(NoiseAction action) {
        repository.setAction(action);
    }

    public BlackListRuleSnapshot addBlackListRule(String pattern) {
        BlackListRule rule = matcher.createRule(pattern, repository.getAction(), true, repository.snapshot());
        repository.add(rule);
        return snapshot(rule);
    }

    public boolean removeBlackListRule(String pattern) {
        return repository.remove(matcher.canonicalize(pattern));
    }

    public boolean setBlackListRuleEnabled(String pattern, boolean enabled) {
        return repository.setEnabled(matcher.canonicalize(pattern), enabled);
    }

    private BlackListRuleSnapshot snapshot(BlackListRule rule) {
        return new BlackListRuleSnapshot(rule.getCanonicalPattern(), rule.getAction(), rule.isEnabled());
    }
}

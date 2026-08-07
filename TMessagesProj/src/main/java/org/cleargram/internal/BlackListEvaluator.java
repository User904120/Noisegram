package org.cleargram.internal;

import java.util.Objects;

import org.cleargram.api.NoiseAction;
import org.cleargram.api.NoiseMessage;

/** Internal boundary for Black List evaluation. */
public final class BlackListEvaluator {

    private final BlackListRepository repository;
    private final BlackListMatcher matcher;

    public static BlackListEvaluator createEmpty() {
        return new BlackListEvaluator(new InMemoryBlackListRepository(), new BlackListMatcher());
    }

    BlackListEvaluator(BlackListRepository repository, BlackListMatcher matcher) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.matcher = Objects.requireNonNull(matcher, "matcher");
    }

    public NoiseAction firstMatch(NoiseMessage message) {
        Objects.requireNonNull(message, "message");
        return matcher.firstMatch(message, repository.enabledSnapshot());
    }

    /** Internal ordered-pipeline entry point for text normalized by NoiseCore. */
    public NoiseAction firstMatchNormalized(String normalizedText) {
        return matcher.firstMatchNormalized(normalizedText, repository.enabledSnapshot());
    }

    public boolean isReady() {
        return repository.isReady();
    }
}

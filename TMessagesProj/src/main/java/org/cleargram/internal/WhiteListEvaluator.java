package org.cleargram.internal;

import java.util.Objects;

import org.cleargram.api.NoiseMessage;

/**
 * Internal boundary for White List evaluation.
 */
public final class WhiteListEvaluator {

    private final WhiteListRepository repository;
    private final WhiteListMatcher matcher;

    public static WhiteListEvaluator createEmpty() {
        return new WhiteListEvaluator(new InMemoryWhiteListRepository(), new WhiteListMatcher());
    }

    WhiteListEvaluator(WhiteListRepository repository, WhiteListMatcher matcher) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.matcher = Objects.requireNonNull(matcher, "matcher");
    }

    public boolean matches(NoiseMessage message) {
        Objects.requireNonNull(message, "message");
        return matcher.matches(message, repository.enabledSnapshot());
    }

    /** Internal ordered-pipeline entry point for text normalized by NoiseCore. */
    public boolean matchesNormalized(String normalizedText) {
        return matcher.matchesNormalized(normalizedText, repository.enabledSnapshot());
    }

    public boolean isReady() {
        return repository.isReady();
    }
}

package org.cleargram.internal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import org.junit.Test;
import org.cleargram.api.NoiseMessage;

public final class WhiteListEvaluatorTest {

    @Test
    public void emptyEvaluatorDoesNotMatch() {
        assertFalse(WhiteListEvaluator.createEmpty().matches(new NoiseMessage("message")));
    }

    @Test
    public void matchingRuleMatchesMessage() {
        WhiteListMatcher matcher = new WhiteListMatcher();
        InMemoryWhiteListRepository repository = new InMemoryWhiteListRepository();
        repository.add(matcher.createRule("important", true, Collections.<WhiteListRule>emptyList()));
        WhiteListEvaluator evaluator = new WhiteListEvaluator(repository, matcher);

        assertTrue(evaluator.matches(new NoiseMessage("An IMPORTANT message")));
    }

    @Test
    public void nonMatchingMessageDoesNotMatch() {
        WhiteListMatcher matcher = new WhiteListMatcher();
        InMemoryWhiteListRepository repository = new InMemoryWhiteListRepository();
        repository.add(matcher.createRule("important", true, Collections.<WhiteListRule>emptyList()));
        WhiteListEvaluator evaluator = new WhiteListEvaluator(repository, matcher);

        assertFalse(evaluator.matches(new NoiseMessage("unrelated message")));
    }

    @Test
    public void disabledRuleDoesNotMatch() {
        WhiteListMatcher matcher = new WhiteListMatcher();
        InMemoryWhiteListRepository repository = new InMemoryWhiteListRepository();
        repository.add(matcher.createRule("important", false, Collections.<WhiteListRule>emptyList()));
        WhiteListEvaluator evaluator = new WhiteListEvaluator(repository, matcher);

        assertFalse(evaluator.matches(new NoiseMessage("important message")));
    }
}

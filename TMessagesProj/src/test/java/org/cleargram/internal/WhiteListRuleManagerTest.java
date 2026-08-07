package org.cleargram.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;
import org.cleargram.api.WhiteListRuleSnapshot;

public final class WhiteListRuleManagerTest {

    @Test
    public void managesRulesThroughImmutableSnapshots() {
        WhiteListRuleManager manager = WhiteListRuleManager.createEmpty();

        manager.addWhiteListRule("  Hello  World ", true);
        manager.addWhiteListRule("Second", false);

        List<WhiteListRuleSnapshot> rules = manager.getWhiteListRules();
        assertEquals("hello world", rules.get(0).getCanonicalPattern());
        assertTrue(rules.get(0).isEnabled());
        assertEquals("second", rules.get(1).getCanonicalPattern());
        assertFalse(rules.get(1).isEnabled());

        try {
            rules.clear();
            throw new AssertionError("snapshot must be immutable");
        } catch (UnsupportedOperationException expected) {
            // Expected immutable snapshot.
        }
    }

    @Test
    public void canonicalizesAdministrativeOperations() {
        WhiteListRuleManager manager = WhiteListRuleManager.createEmpty();
        manager.addWhiteListRule("Important", true);

        assertTrue(manager.setWhiteListRuleEnabled("  IMPORTANT ", false));
        assertFalse(manager.getWhiteListRules().get(0).isEnabled());
        assertTrue(manager.removeWhiteListRule(" IMPORTANT "));
        assertFalse(manager.removeWhiteListRule("important"));
        assertFalse(manager.setWhiteListRuleEnabled("missing", true));
    }

    @Test
    public void evaluatorSharesManagerRepository() {
        WhiteListRuleManager manager = WhiteListRuleManager.createEmpty();
        WhiteListEvaluator evaluator = manager.createEvaluator();

        manager.addWhiteListRule("Important", true);

        assertTrue(evaluator.matches(new org.cleargram.api.NoiseMessage("IMPORTANT")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsDuplicateCanonicalPattern() {
        WhiteListRuleManager manager = WhiteListRuleManager.createEmpty();
        manager.addWhiteListRule("Important", true);
        manager.addWhiteListRule(" important ", false);
    }
}

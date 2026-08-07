package org.cleargram.internal;

import org.junit.Test;
import org.cleargram.api.NoiseAction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class BlackListRuleManagerTest {
    @Test public void globalActionControlsEnabledNewRulesAndExistingRules() {
        BlackListRuleManager manager = BlackListRuleManager.createEmpty();
        assertEquals(NoiseAction.COLLAPSE, manager.getBlackListAction());
        manager.addBlackListRule("first");
        manager.setBlackListAction(NoiseAction.HIDE);
        manager.addBlackListRule("second");
        assertEquals(NoiseAction.HIDE, manager.getBlackListRules().get(0).getAction());
        assertEquals(NoiseAction.HIDE, manager.getBlackListRules().get(1).getAction());
        assertTrue(manager.getBlackListRules().get(1).isEnabled());
    }
    @Test public void matcherStillCanonicalizesAndRejectsDuplicates() {
        BlackListRuleManager manager = BlackListRuleManager.createEmpty();
        manager.addBlackListRule("  One\u2003Two ");
        assertEquals("one two", manager.getBlackListRules().get(0).getCanonicalPattern());
        try { manager.addBlackListRule("one two"); fail("Expected duplicate rejection"); }
        catch (IllegalArgumentException expected) { }
    }
}

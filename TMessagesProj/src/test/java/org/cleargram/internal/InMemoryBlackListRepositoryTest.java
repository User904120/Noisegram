package org.cleargram.internal;

import java.util.List;

import org.junit.Test;
import org.cleargram.api.NoiseAction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class InMemoryBlackListRepositoryTest {

    @Test
    public void defaultActionIsCollapseAndSetActionPreservesRuleIdentityOrderAndEnabled() {
        InMemoryBlackListRepository repository = new InMemoryBlackListRepository();
        repository.add(new BlackListRule("first", NoiseAction.HIDE, true));
        repository.add(new BlackListRule("second", NoiseAction.COLLAPSE, false));

        assertEquals(NoiseAction.COLLAPSE, repository.getAction());
        repository.setAction(NoiseAction.HIDE);
        repository.setAction(NoiseAction.HIDE);

        List<BlackListRule> rules = repository.snapshot();
        assertEquals(NoiseAction.HIDE, repository.getAction());
        assertEquals("first", rules.get(0).getCanonicalPattern());
        assertTrue(rules.get(0).isEnabled());
        assertEquals("second", rules.get(1).getCanonicalPattern());
        assertFalse(rules.get(1).isEnabled());
        assertEquals(NoiseAction.HIDE, rules.get(0).getAction());
        assertEquals(NoiseAction.HIDE, rules.get(1).getAction());
    }

    @Test
    public void rejectsUnsupportedActions() {
        InMemoryBlackListRepository repository = new InMemoryBlackListRepository();
        expectArgument(() -> repository.setAction(null));
        expectArgument(() -> repository.setAction(NoiseAction.ALLOW));
    }

    private static void expectArgument(Runnable runnable) {
        try { runnable.run(); fail("Expected IllegalArgumentException"); }
        catch (IllegalArgumentException expected) { }
    }
}

package org.cleargram.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.junit.Test;

public final class WhiteListRuleSnapshotTest {

    @Test
    public void gettersExposeImmutableSnapshotValues() {
        WhiteListRuleSnapshot snapshot = new WhiteListRuleSnapshot("important", true);

        assertEquals("important", snapshot.getCanonicalPattern());
        assertTrue(snapshot.isEnabled());
        assertFalse(hasSetter());
    }

    @Test(expected = NullPointerException.class)
    public void rejectsNullPattern() {
        new WhiteListRuleSnapshot(null, true);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsEmptyPattern() {
        new WhiteListRuleSnapshot("", false);
    }

    private static boolean hasSetter() {
        for (Method method : WhiteListRuleSnapshot.class.getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers()) && method.getName().startsWith("set")) {
                return true;
            }
        }
        return false;
    }
}

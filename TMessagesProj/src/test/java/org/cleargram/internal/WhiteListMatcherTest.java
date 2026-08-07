package org.cleargram.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import org.junit.Test;
import org.cleargram.api.NoiseMessage;

public final class WhiteListMatcherTest {

    private final WhiteListMatcher matcher = new WhiteListMatcher();

    @Test
    public void canonicalizesUnicodeWhitespace() {
        WhiteListRule rule = matcher.createRule("\u2003  Hello\u00a0\tworld  ", true, Collections.<WhiteListRule>emptyList());

        assertEquals("hello world", rule.getCanonicalPattern());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsEmptyPattern() {
        matcher.createRule("", true, Collections.<WhiteListRule>emptyList());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsWhitespaceOnlyPattern() {
        matcher.createRule("\u2003\u00a0\t", true, Collections.<WhiteListRule>emptyList());
    }

    @Test
    public void enforces255UnicodeCodePointLimit() {
        WhiteListRule rule = matcher.createRule(repeatCodePoint("\uD83D\uDE00", 255), true, Collections.<WhiteListRule>emptyList());

        assertEquals(255, rule.getCanonicalPattern().codePointCount(0, rule.getCanonicalPattern().length()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejects256EmojiCodePoints() {
        matcher.createRule(repeatCodePoint("\uD83D\uDE00", 256), true, Collections.<WhiteListRule>emptyList());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsDuplicateAfterCaseFoldAndWhitespaceNormalization() {
        WhiteListRule existing = matcher.createRule("  Hello  World ", true, Collections.<WhiteListRule>emptyList());

        matcher.createRule("hello\tworld", false, Collections.singletonList(existing));
    }

    @Test
    public void matchesLatinAndCyrillicSubstringsWithoutCaseSensitivity() {
        WhiteListRule latin = matcher.createRule("telegram", true, Collections.<WhiteListRule>emptyList());
        WhiteListRule cyrillic = matcher.createRule("Привет", true, Collections.singletonList(latin));

        assertTrue(matcher.matches(new NoiseMessage("A TELEGRAM message"), Collections.singletonList(latin)));
        assertTrue(matcher.matches(new NoiseMessage("Скажи ПРИВЕТ всем"), Collections.singletonList(cyrillic)));
    }

    @Test
    public void matchesEmojiSubstring() {
        WhiteListRule rule = matcher.createRule("\uD83D\uDE00", true, Collections.<WhiteListRule>emptyList());

        assertTrue(matcher.matches(new NoiseMessage("status \uD83D\uDE00 now"), Collections.singletonList(rule)));
    }

    @Test
    public void enabledRuleMatches() {
        WhiteListRule rule = matcher.createRule("important", true, Collections.<WhiteListRule>emptyList());

        assertTrue(matcher.matches(new NoiseMessage("Important message"), Collections.singletonList(rule)));
    }

    @Test
    public void disabledRuleDoesNotMatch() {
        WhiteListRule rule = matcher.createRule("important", false, Collections.<WhiteListRule>emptyList());

        assertFalse(matcher.matches(new NoiseMessage("Important message"), Collections.singletonList(rule)));
    }

    @Test
    public void noMatchReturnsFalse() {
        WhiteListRule rule = matcher.createRule("important", true, Collections.<WhiteListRule>emptyList());

        assertFalse(matcher.matches(new NoiseMessage("unrelated message"), Collections.singletonList(rule)));
    }

    @Test
    public void matchesNormalizedUsesTheProvidedCanonicalText() {
        WhiteListRule rule = matcher.createRule("important", true, Collections.<WhiteListRule>emptyList());

        assertTrue(matcher.matchesNormalized("an important message", Collections.singletonList(rule)));
        assertFalse(matcher.matchesNormalized("an unrelated message", Collections.singletonList(rule)));
    }

    private static String repeatCodePoint(String codePoint, int count) {
        StringBuilder value = new StringBuilder(codePoint.length() * count);
        for (int i = 0; i < count; i++) {
            value.append(codePoint);
        }
        return value.toString();
    }
}

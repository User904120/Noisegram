package org.cleargram.internal;

import java.util.Locale;
import java.util.Objects;

import org.cleargram.api.NoiseMessage;

/**
 * Internal White List rule canonicalization and matching service.
 */
final class WhiteListMatcher {

    private static final int MAX_PATTERN_CODE_POINTS = 255;

    WhiteListRule createRule(String pattern, boolean enabled, Iterable<WhiteListRule> existingRules) {
        Objects.requireNonNull(existingRules, "existingRules");
        String canonicalPattern = canonicalize(pattern);
        for (WhiteListRule existingRule : existingRules) {
            if (existingRule == null) {
                throw new IllegalArgumentException("existingRules must not contain null");
            }
            if (canonicalPattern.equals(existingRule.getCanonicalPattern())) {
                throw new IllegalArgumentException("duplicate White List pattern");
            }
        }
        return new WhiteListRule(canonicalPattern, enabled);
    }

    boolean matches(NoiseMessage message, Iterable<WhiteListRule> rules) {
        Objects.requireNonNull(message, "message");
        return matchesNormalized(message.getText().toLowerCase(Locale.ROOT), rules);
    }

    boolean matchesNormalized(String normalizedText, Iterable<WhiteListRule> rules) {
        Objects.requireNonNull(normalizedText, "normalizedText");
        Objects.requireNonNull(rules, "rules");
        for (WhiteListRule rule : rules) {
            if (rule == null) {
                throw new IllegalArgumentException("rules must not contain null");
            }
            if (rule.isEnabled() && normalizedText.contains(rule.getCanonicalPattern())) {
                return true;
            }
        }
        return false;
    }

    String canonicalize(String pattern) {
        Objects.requireNonNull(pattern, "pattern");
        StringBuilder normalized = new StringBuilder(pattern.length());
        boolean pendingWhitespace = false;
        for (int offset = 0; offset < pattern.length();) {
            int codePoint = pattern.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (isUnicodeWhitespace(codePoint)) {
                if (normalized.length() > 0) {
                    pendingWhitespace = true;
                }
            } else {
                if (pendingWhitespace) {
                    normalized.append(' ');
                    pendingWhitespace = false;
                }
                normalized.appendCodePoint(codePoint);
            }
        }

        String canonicalPattern = normalized.toString().toLowerCase(Locale.ROOT);
        if (canonicalPattern.isEmpty()) {
            throw new IllegalArgumentException("White List pattern must not be empty");
        }
        if (canonicalPattern.codePointCount(0, canonicalPattern.length()) > MAX_PATTERN_CODE_POINTS) {
            throw new IllegalArgumentException("White List pattern is too long");
        }
        return canonicalPattern;
    }

    private boolean isUnicodeWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }
}

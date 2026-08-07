package org.cleargram.internal;

import java.util.Locale;
import java.util.Objects;

import org.cleargram.api.NoiseAction;
import org.cleargram.api.NoiseMessage;

/** Internal Black List rule canonicalization and matching service. */
final class BlackListMatcher {

    private static final int MAX_PATTERN_CODE_POINTS = 255;

    BlackListRule createRule(
            String pattern,
            NoiseAction action,
            boolean enabled,
            Iterable<BlackListRule> existingRules
    ) {
        Objects.requireNonNull(existingRules, "existingRules");
        String canonicalPattern = canonicalize(pattern);
        for (BlackListRule existingRule : existingRules) {
            if (existingRule == null) {
                throw new IllegalArgumentException("existingRules must not contain null");
            }
            if (canonicalPattern.equals(existingRule.getCanonicalPattern())) {
                throw new IllegalArgumentException("duplicate Black List pattern");
            }
        }
        return new BlackListRule(canonicalPattern, action, enabled);
    }

    NoiseAction firstMatch(NoiseMessage message, Iterable<BlackListRule> rules) {
        Objects.requireNonNull(message, "message");
        return firstMatchNormalized(message.getText().toLowerCase(Locale.ROOT), rules);
    }

    NoiseAction firstMatchNormalized(String normalizedText, Iterable<BlackListRule> rules) {
        Objects.requireNonNull(normalizedText, "normalizedText");
        Objects.requireNonNull(rules, "rules");
        int ruleIndex = 0;
        for (BlackListRule rule : rules) {
            if (rule == null) {
                throw new IllegalArgumentException("rules must not contain null");
            }
            int matchStart = rule.isEnabled() ? normalizedText.indexOf(rule.getCanonicalPattern()) : -1;
            if (matchStart >= 0) {
                BlackListMatchDiagnostics.logMatch(ruleIndex, rule.getCanonicalPattern().length(),
                        matchStart, rule.getAction());
                return rule.getAction();
            }
            ruleIndex++;
        }
        return null;
    }

    String canonicalize(String pattern) {
        Objects.requireNonNull(pattern, "pattern");
        StringBuilder normalized = new StringBuilder(pattern.length());
        boolean pendingWhitespace = false;
        for (int offset = 0; offset < pattern.length();) {
            int codePoint = pattern.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
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
            throw new IllegalArgumentException("Black List pattern must not be empty");
        }
        if (canonicalPattern.codePointCount(0, canonicalPattern.length()) > MAX_PATTERN_CODE_POINTS) {
            throw new IllegalArgumentException("Black List pattern is too long");
        }
        return canonicalPattern;
    }
}

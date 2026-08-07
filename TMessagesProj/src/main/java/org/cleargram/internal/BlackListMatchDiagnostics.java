package org.cleargram.internal;

import java.util.logging.Logger;

import org.cleargram.api.NoiseAction;

/** Core-local trace emitted from the same matcher operation that selects an action. */
final class BlackListMatchDiagnostics {

    private static final Logger LOGGER = Logger.getLogger("CleargramBlackList");

    private BlackListMatchDiagnostics() {
    }

    static void logMatch(int ruleIndex, int ruleLength, int start, NoiseAction action) {
        try {
            LOGGER.info(formatMatch(ruleIndex, ruleLength, start, action));
        } catch (Throwable ignored) {
            // Diagnostics must not affect Black List evaluation.
        }
    }

    static String formatMatch(int ruleIndex, int ruleLength, int start, NoiseAction action) {
        int safeStart = Math.max(0, start);
        int safeRuleLength = Math.max(0, ruleLength);
        int end = safeStart + safeRuleLength;
        return "phase=match threadId=" + Thread.currentThread().getId()
                + " ruleIndex=" + ruleIndex
                + " ruleLength=" + safeRuleLength
                + " start=" + safeStart
                + " end=" + end
                + " action=" + action;
    }
}

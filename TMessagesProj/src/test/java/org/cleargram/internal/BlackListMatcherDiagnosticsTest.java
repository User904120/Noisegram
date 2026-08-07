package org.cleargram.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.junit.Test;
import org.cleargram.api.NoiseAction;
import org.cleargram.api.NoiseMessage;

public final class BlackListMatcherDiagnosticsTest {

    @Test
    public void diagnosticsUsesTheSameFirstMatchAndPreservesHideAndCollapseActions() {
        BlackListMatcher matcher = new BlackListMatcher();
        int index = 0;
        for (String pattern : Arrays.asList("#реклама", "рекламодатель", "erid:", "ставки", "каппер")) {
            NoiseAction action = index++ % 2 == 0 ? NoiseAction.HIDE : NoiseAction.COLLAPSE;
            BlackListRule rule = matcher.createRule(pattern, action, true,
                    Arrays.<BlackListRule>asList());
            assertEquals(action,
                    matcher.firstMatch(new NoiseMessage("prefix " + pattern + " suffix"),
                            Arrays.asList(rule)));
        }
    }

    @Test
    public void matchDiagnosticReportsIndexRangeActionAndNoRuleText() {
        String entry = BlackListMatchDiagnostics.formatMatch(4, 5, 9,
                NoiseAction.HIDE);

        assertTrue(entry.contains("threadId=" + Thread.currentThread().getId()));
        assertTrue(entry.contains("ruleIndex=4"));
        assertTrue(entry.contains("ruleLength=5"));
        assertTrue(entry.contains("start=9"));
        assertTrue(entry.contains("end=14"));
        assertTrue(entry.contains("action=HIDE"));
        assertTrue(!entry.contains("erid:"));
    }

    @Test
    public void mapperAndMatcherFormattersUseTheSameCurrentThreadId() throws Exception {
        Method mapperFormatter = Class.forName("org.telegram.ui.TelegramBlackListDiagnostics")
                .getDeclaredMethod("formatMappedMetadata", boolean.class, int.class, int.class);
        mapperFormatter.setAccessible(true);
        String mappedEntry = (String) mapperFormatter.invoke(null, false, 3, 1);
        String matchEntry = BlackListMatchDiagnostics.formatMatch(0, 4, 3, NoiseAction.COLLAPSE);
        String threadId = "threadId=" + Thread.currentThread().getId();

        assertTrue(mappedEntry.contains(threadId));
        assertTrue(matchEntry.contains(threadId));
    }
}

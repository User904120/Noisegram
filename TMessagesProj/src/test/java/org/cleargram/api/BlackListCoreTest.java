package org.cleargram.api;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public final class BlackListCoreTest {
    @Test public void globalActionDrivesNewRulesAndFirstMatch() {
        NoiseCore core = new NoiseCore();
        core.setBlackListAction(NoiseAction.HIDE);
        core.addBlackListRule("spam");
        core.setBlackListAction(NoiseAction.COLLAPSE);
        core.addBlackListRule("offer");
        assertEquals(NoiseAction.COLLAPSE, core.getBlackListRules().get(0).getAction());
        assertEquals(NoiseAction.COLLAPSE, core.evaluate(new NoiseMessage("spam offer")).getAction());
    }
    @Test public void whiteListRetainsTerminalPriority() {
        NoiseCore core = new NoiseCore();
        core.setBlackListAction(NoiseAction.HIDE);
        core.addWhiteListRule("approved", true);
        core.addBlackListRule("approved");
        assertEquals(NoiseAction.ALLOW, core.evaluate(new NoiseMessage("approved")).getAction());
    }
}

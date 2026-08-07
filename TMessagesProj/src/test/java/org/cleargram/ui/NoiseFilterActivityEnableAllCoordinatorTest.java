package org.cleargram.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class NoiseFilterActivityEnableAllCoordinatorTest {

    @Test
    public void startSetsPendingCount() {
        NoiseFilterActivity.EnableAllCoordinator c = new NoiseFilterActivity.EnableAllCoordinator();
        assertTrue(c.start(6));
        assertEquals(6, c.getPendingWrites());
        assertTrue(c.isUpdating());
    }

    @Test
    public void duplicateStartRejected() {
        NoiseFilterActivity.EnableAllCoordinator c = new NoiseFilterActivity.EnableAllCoordinator();
        assertTrue(c.start(3));
        assertFalse(c.start(2));
        assertEquals(3, c.getPendingWrites());
    }

    @Test
    public void firstCallbackDoesNotCompletePrematurely() {
        NoiseFilterActivity.EnableAllCoordinator c = new NoiseFilterActivity.EnableAllCoordinator();
        c.start(3);
        NoiseFilterActivity.EnableAllCoordinator.CallbackResult r =
                c.onCallback(c.getGeneration(), false, true);
        assertEquals(NoiseFilterActivity.EnableAllCoordinator.CallbackResult.PENDING, r);
        assertTrue(c.isUpdating());
        assertEquals(2, c.getPendingWrites());
    }

    @Test
    public void lastSuccessCompletesOk() {
        NoiseFilterActivity.EnableAllCoordinator c = new NoiseFilterActivity.EnableAllCoordinator();
        c.start(1);
        NoiseFilterActivity.EnableAllCoordinator.CallbackResult r =
                c.onCallback(c.getGeneration(), false, true);
        assertEquals(NoiseFilterActivity.EnableAllCoordinator.CallbackResult.COMPLETED_OK, r);
        assertFalse(c.isUpdating());
        assertFalse(c.hadError());
    }

    @Test
    public void oneFailurePreservesErrorUntilDone() {
        NoiseFilterActivity.EnableAllCoordinator c = new NoiseFilterActivity.EnableAllCoordinator();
        c.start(3);
        int gen = c.getGeneration();
        c.onCallback(gen, false, false);
        assertTrue(c.hadError());
        NoiseFilterActivity.EnableAllCoordinator.CallbackResult r1 =
                c.onCallback(gen, false, true);
        assertEquals(NoiseFilterActivity.EnableAllCoordinator.CallbackResult.PENDING, r1);
        NoiseFilterActivity.EnableAllCoordinator.CallbackResult r2 =
                c.onCallback(gen, false, true);
        assertEquals(NoiseFilterActivity.EnableAllCoordinator.CallbackResult.COMPLETED_ERROR, r2);
        assertTrue(c.hadError());
    }

    @Test
    public void multipleFailuresProduceSingleErrorResult() {
        NoiseFilterActivity.EnableAllCoordinator c = new NoiseFilterActivity.EnableAllCoordinator();
        c.start(2);
        int gen = c.getGeneration();
        c.onCallback(gen, false, false);
        NoiseFilterActivity.EnableAllCoordinator.CallbackResult r =
                c.onCallback(gen, false, false);
        assertEquals(NoiseFilterActivity.EnableAllCoordinator.CallbackResult.COMPLETED_ERROR, r);
    }

    @Test
    public void staleGenerationIgnored() {
        NoiseFilterActivity.EnableAllCoordinator c =
                new NoiseFilterActivity.EnableAllCoordinator();

        assertTrue(c.start(2));
        int staleGeneration = c.getGeneration();

        c.invalidate();

        assertTrue(c.start(1));
        int currentGeneration = c.getGeneration();
        assertTrue(currentGeneration > staleGeneration);

        NoiseFilterActivity.EnableAllCoordinator.CallbackResult result =
                c.onCallback(staleGeneration, false, false);

        assertEquals(
                NoiseFilterActivity.EnableAllCoordinator.CallbackResult.IGNORED,
                result);
        assertTrue(c.isUpdating());
        assertEquals(1, c.getPendingWrites());
        assertFalse(c.hadError());
    }

    @Test
    public void destroyedCallbackIgnored() {
        NoiseFilterActivity.EnableAllCoordinator c = new NoiseFilterActivity.EnableAllCoordinator();
        c.start(2);
        int gen = c.getGeneration();
        NoiseFilterActivity.EnableAllCoordinator.CallbackResult r =
                c.onCallback(gen, true, true);
        assertEquals(NoiseFilterActivity.EnableAllCoordinator.CallbackResult.IGNORED, r);
        assertEquals(2, c.getPendingWrites());
    }

    @Test
    public void stopsUpdatingAfterCompletion() {
        NoiseFilterActivity.EnableAllCoordinator c = new NoiseFilterActivity.EnableAllCoordinator();
        c.start(1);
        c.onCallback(c.getGeneration(), false, true);
        assertFalse(c.isUpdating());
    }

    @Test
    public void newBatchGetsNewGeneration() {
        NoiseFilterActivity.EnableAllCoordinator c = new NoiseFilterActivity.EnableAllCoordinator();
        c.start(1);
        int gen1 = c.getGeneration();
        c.onCallback(gen1, false, true);
        c.start(1);
        int gen2 = c.getGeneration();
        assertTrue(gen2 > gen1);
    }

    @Test
    public void staleBatchCallbackDoesNotAffectNewBatch() {
        NoiseFilterActivity.EnableAllCoordinator c = new NoiseFilterActivity.EnableAllCoordinator();
        c.start(1);
        int oldGen = c.getGeneration();
        c.onCallback(oldGen, false, true);
        c.start(1);
        int newGen = c.getGeneration();
        NoiseFilterActivity.EnableAllCoordinator.CallbackResult r =
                c.onCallback(oldGen, false, false);
        assertEquals(NoiseFilterActivity.EnableAllCoordinator.CallbackResult.IGNORED, r);
        assertFalse(c.hadError());
        assertEquals(1, c.getPendingWrites());
    }

    @Test
    public void zeroWriteBatchCompletesImmediately() {
        NoiseFilterActivity.EnableAllCoordinator c = new NoiseFilterActivity.EnableAllCoordinator();
        assertTrue(c.start(0));
        assertFalse(c.isUpdating());
        assertEquals(0, c.getPendingWrites());
        assertFalse(c.hadError());
    }
}

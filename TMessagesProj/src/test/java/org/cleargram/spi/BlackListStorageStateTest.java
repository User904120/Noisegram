package org.cleargram.spi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import org.cleargram.api.NoiseAction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public final class BlackListStorageStateTest {

    @Test
    public void copiesAndExposesImmutableRecords() {
        List<BlackListStorageRecord> source = new ArrayList<>();
        source.add(new BlackListStorageRecord("rule", NoiseAction.HIDE, true));
        BlackListStorageState state = new BlackListStorageState(NoiseAction.COLLAPSE, source);
        source.clear();

        assertEquals(1, state.getRecords().size());
        try { state.getRecords().clear(); fail("records must be immutable"); }
        catch (UnsupportedOperationException expected) { }
    }

    @Test
    public void rejectsNullInputsAndRecords() {
        expectNull(() -> new BlackListStorageState(null, Collections.emptyList()));
        expectNull(() -> new BlackListStorageState(NoiseAction.HIDE, null));
        List<BlackListStorageRecord> records = new ArrayList<>();
        records.add(null);
        expectNull(() -> new BlackListStorageState(NoiseAction.HIDE, records));
    }

    private static void expectNull(Runnable runnable) {
        try { runnable.run(); fail("Expected NullPointerException"); }
        catch (NullPointerException expected) { }
    }
}

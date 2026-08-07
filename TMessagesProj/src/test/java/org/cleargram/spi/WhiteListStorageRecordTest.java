package org.cleargram.spi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class WhiteListStorageRecordTest {

    @Test
    public void storesOnlyTheContractFields() {
        WhiteListStorageRecord record = new WhiteListStorageRecord("pattern", true);

        assertEquals("pattern", record.getCanonicalPattern());
        assertTrue(record.isEnabled());
        assertFalse(new WhiteListStorageRecord("pattern", false).isEnabled());
    }

    @Test
    public void preservesCanonicalPatternWithoutCanonicalization() {
        WhiteListStorageRecord record = new WhiteListStorageRecord("  Mixed  Pattern  ", true);

        assertEquals("  Mixed  Pattern  ", record.getCanonicalPattern());
    }

    @Test(expected = NullPointerException.class)
    public void rejectsNullPattern() {
        new WhiteListStorageRecord(null, true);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsEmptyPattern() {
        new WhiteListStorageRecord("", true);
    }
}

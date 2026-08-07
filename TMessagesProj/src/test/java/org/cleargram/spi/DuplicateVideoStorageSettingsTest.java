package org.cleargram.spi;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class DuplicateVideoStorageSettingsTest {

    @Test
    public void preservesRawDurableValuesWithoutValidationOrDefaulting() {
        DuplicateVideoStorageSettings settings = new DuplicateVideoStorageSettings(-7, 99);
        assertEquals(-7, settings.getEnabled());
        assertEquals(99, settings.getMatchMode());
    }

    @Test
    public void preservesIndependentRawValues() {
        DuplicateVideoStorageSettings settings = new DuplicateVideoStorageSettings(1, 2);
        assertEquals(1, settings.getEnabled());
        assertEquals(2, settings.getMatchMode());
    }
}

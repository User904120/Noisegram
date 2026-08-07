package org.cleargram.api;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

public final class DuplicateVideoItemTest {

    @Test
    public void acceptsAnyPresentationItemIdWithThirtyTwoByteKey() {
        long[] ids = {-1L, 0L, Long.MIN_VALUE, Long.MAX_VALUE};
        for (long id : ids) {
            DuplicateVideoItem item = new DuplicateVideoItem(id, key((byte) 1));
            assertEquals(id, item.getPresentationItemId());
            assertArrayEquals(key((byte) 1), item.getMatchKey());
        }
    }

    @Test(expected = NullPointerException.class)
    public void rejectsNullKey() {
        new DuplicateVideoItem(1L, null);
    }

    @Test
    public void rejectsInvalidKeyLengths() {
        assertInvalidLength(0);
        assertInvalidLength(31);
        assertInvalidLength(33);
    }

    @Test
    public void defensivelyCopiesKeyOnInputAndOutput() {
        byte[] source = key((byte) 3);
        DuplicateVideoItem item = new DuplicateVideoItem(1L, source);
        source[0] = 9;
        assertEquals(3, item.getMatchKey()[0]);

        byte[] returned = item.getMatchKey();
        returned[1] = 8;
        assertEquals(3, item.getMatchKey()[1]);
    }

    private static void assertInvalidLength(int length) {
        try {
            new DuplicateVideoItem(1L, new byte[length]);
            fail("Expected invalid key length " + length);
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    static byte[] key(byte value) {
        byte[] key = new byte[32];
        for (int index = 0; index < key.length; index++) {
            key[index] = value;
        }
        return key;
    }
}

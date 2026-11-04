package mpo.dayon.common.squeeze;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CompressionMethodTest {

    @Test
    void shouldHaveExpectedEnumValues() {
        // given
        CompressionMethod[] values = CompressionMethod.values();
        // then
        assertEquals(3, values.length);
        assertEquals(CompressionMethod.NONE, values[0]);
        assertEquals(CompressionMethod.ZIP, values[1]);
        assertEquals(CompressionMethod.XZ, values[2]);
    }

    @Test
    void ordinalOfNoneShouldBeZero() {
        // when
        int ordinal = CompressionMethod.NONE.ordinal();
        // then
        assertEquals(0, ordinal);
    }

    @Test
    void ordinalOfZipShouldBeOne() {
        // when
        int ordinal = CompressionMethod.ZIP.ordinal();
        // then
        assertEquals(1, ordinal);
    }

    @Test
    void ordinalOfXzShouldBeTwo() {
        // when
        int ordinal = CompressionMethod.XZ.ordinal();
        // then
        assertEquals(2, ordinal);
    }
}

package mpo.dayon.common.gui.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PositionTest {

    @Test
    void getters() {
        // given, when
        Position position = new Position(11, 22);

        // then
        assertEquals(11, position.getX());
        assertEquals(22, position.getY());
    }

    @Test
    void equalsAndHashCode() {
        // given
        Position first = new Position(10, 20);
        Position second = new Position(10, 20);
        Position otherX = new Position(11, 20);
        Position otherY = new Position(10, 21);

        // when, then
        assertEquals(first, first);
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, otherX);
        assertNotEquals(first, otherY);
        assertNotEquals(first, null);
        assertNotEquals(first, new Object());
    }
}

package mpo.dayon.common.gui.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FrameTypeTest {

    @Test
    void getPrefix() {
        assertEquals("assistant", FrameType.ASSISTANT.getPrefix());
        assertEquals("assisted", FrameType.ASSISTED.getPrefix());
    }

    @Test
    void getMinHeightWindows() {
        assertEquals(320, FrameType.ASSISTANT.getMinHeight());
        assertEquals(640, FrameType.ASSISTANT.getMinWidth());
        assertEquals(100, FrameType.ASSISTED.getMinHeight());
        assertEquals(640, FrameType.ASSISTED.getMinWidth());
    }
}
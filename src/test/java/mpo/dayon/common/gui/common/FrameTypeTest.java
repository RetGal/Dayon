package mpo.dayon.common.gui.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.condition.OS.LINUX;
import static org.junit.jupiter.api.condition.OS.WINDOWS;

class FrameTypeTest {

    @Test
    void getPrefix() {
        assertEquals("assistant", FrameType.ASSISTANT.getPrefix());
        assertEquals("assisted", FrameType.ASSISTED.getPrefix());
    }

    @Test
    @DisabledOnOs(WINDOWS)
    void getMinDimensionsLinux() {
        assertEquals(320, FrameType.ASSISTANT.getMinHeight());
        assertEquals(640, FrameType.ASSISTANT.getMinWidth());
        assertEquals(60, FrameType.ASSISTED.getMinHeight());
        assertEquals(640, FrameType.ASSISTED.getMinWidth());
    }

    @Test
    @DisabledOnOs(LINUX)
    void getMinHeightWindows() {
        assertEquals(320, FrameType.ASSISTANT.getMinHeight());
        assertEquals(640, FrameType.ASSISTANT.getMinWidth());
        assertEquals(100, FrameType.ASSISTED.getMinHeight());
        assertEquals(640, FrameType.ASSISTED.getMinWidth());
    }
}
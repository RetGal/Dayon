package mpo.dayon.common.gui.common;

import org.junit.jupiter.api.Test;

import javax.swing.*;

import static org.junit.jupiter.api.Assertions.*;

class ImageUtilitiesTest {

    @Test
    void getOrCreateIcon() {
        // given when
        ImageIcon icon = ImageUtilities.getOrCreateIcon(ImageNames.FINGERPRINT);

        // then
        assertEquals(24, icon.getIconWidth(), "Width should be 24");
        assertEquals(24, icon.getIconHeight(), "Height should be 24");
    }

    @Test
    void getOrCreateWaitingIcon() {
        // given when
        ImageIcon icon = ImageUtilities.getOrCreateIcon(ImageNames.WAITING);

        // then
        assertEquals(33, icon.getIconWidth(), "Width should be 33");
        assertEquals(32, icon.getIconHeight(), "Height should be 32");
    }
}
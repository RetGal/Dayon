package mpo.dayon.common.gui.common;

import org.junit.jupiter.api.Test;

import javax.swing.*;

import static org.junit.jupiter.api.Assertions.*;

class ImageUtilitiesTest {

    @Test
    void getOrCreateIcon() {
        // given when
        ImageIcon Icon = ImageUtilities.getOrCreateIcon(ImageNames.FINGERPRINT);

        // then
        assertEquals(24, Icon.getIconWidth(), "Width should be 24");
        assertEquals(24, Icon.getIconHeight(), "Height should be 24");
    }

    @Test
    void getOrCreateWaitingIcon() {
        // given when
        ImageIcon Icon = ImageUtilities.getOrCreateIcon(ImageNames.WAITING);

        // then
        assertEquals(33, Icon.getIconWidth(), "Width should be 33");
        assertEquals(32, Icon.getIconHeight(), "Height should be 32");
    }
}
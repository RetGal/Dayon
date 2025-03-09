package mpo.dayon.assistant.gui;

import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import static mpo.dayon.common.gui.common.ImageNames.MOUSE_YELLOW;
import static mpo.dayon.common.gui.common.ImageUtilities.getOrCreateIcon;

class AssistantPanel extends JPanel {

	private static final Image MOUSE_CURSOR = getOrCreateIcon(MOUSE_YELLOW).getImage();
	private static final int MOUSE_CURSOR_WIDTH = 12;
	private static final int MOUSE_CURSOR_HEIGHT = 20;

	private int captureWidth = -1;
	private int captureHeight = -1;

	private int mouseX = -1;
	private int mouseY = -1;

	private transient BufferedImage captureImage;

	AssistantPanel() {
		setOpaque(true);
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		if (captureImage != null) {
			g.drawImage(captureImage, 0, 0, this);
		}
		if (mouseX > -1 && mouseY > -1) {
			g.drawImage(MOUSE_CURSOR, mouseX, mouseY, this);
		}
	}

	/**
	 * Called from within the de-compressor engine thread (!)
	 */
	void onCaptureUpdated(final BufferedImage captureImage) {
		SwingUtilities.invokeLater(() -> {
            final int captureImageWidth = captureImage.getWidth();
            final int captureImageHeight = captureImage.getHeight();
            if (captureWidth != captureImageWidth || captureHeight != captureImageHeight) {
                this.captureWidth = captureImageWidth;
                this.captureHeight = captureImageHeight;
                setSize(captureImageWidth, captureImageHeight);
                setPreferredSize(getSize());
            }
            this.captureImage = captureImage;
            repaint();
        });
	}

	void onMouseLocationUpdated(final int x, final int y) {
		SwingUtilities.invokeLater(() -> {
			if (mouseX > -1 && mouseY > -1) {
				repaint(mouseX, mouseY, MOUSE_CURSOR_WIDTH, MOUSE_CURSOR_HEIGHT);
			}
            mouseX = x;
            mouseY = y;
            repaint(x, y, MOUSE_CURSOR_WIDTH, MOUSE_CURSOR_HEIGHT);
        });
	}
}

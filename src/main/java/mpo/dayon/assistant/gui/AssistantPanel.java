package mpo.dayon.assistant.gui;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.jetbrains.annotations.Nullable;

import mpo.dayon.common.gui.common.ImageNames;
import mpo.dayon.common.gui.common.ImageUtilities;

class AssistantPanel extends JPanel {

	private static final Image MOUSE_CURSOR = ImageUtilities.getOrCreateIcon(ImageNames.MOUSE_YELLOW).getImage();

	private static final int MOUSE_CURSOR_WIDTH = 12;

	private static final int MOUSE_CURSOR_HEIGHT = 20;

	@Nullable
	private transient BufferedImage captureImage;

	private int captureWidth = -1;

	private int captureHeight = -1;

	private int mouseX = -1;

	private int mouseY = -1;

	public AssistantPanel() {
		setOpaque(true);
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);

		if (captureImage != null) {
			g.drawImage(captureImage, 0, 0, this);
		}

		if (mouseX > -1 && mouseY > -1) {
			paintMouse(g);
		}
	}

	private void paintMouse(Graphics g) {
		if (mouseX > -1 && mouseY > -1) {
			g.drawImage(MOUSE_CURSOR, mouseX, mouseY, this);
		}
	}

	/**
	 * Called from within the de-compressor engine thread (!)
	 */
	public void onCaptureUpdated(final BufferedImage captureImage) {
		SwingUtilities.invokeLater(() -> {
            final int captureWidth = captureImage.getWidth();
            final int captureHeight = captureImage.getHeight();

            if (AssistantPanel.this.captureWidth != captureWidth || AssistantPanel.this.captureHeight != captureHeight) {
                AssistantPanel.this.captureImage = null;

                AssistantPanel.this.captureWidth = captureWidth;
                AssistantPanel.this.captureHeight = captureHeight;

                final Dimension size = new Dimension(captureWidth, captureHeight);

                setSize(size);
                setPreferredSize(size);
            }

            AssistantPanel.this.captureImage = captureImage;

            repaint();
        });
	}

	public void onMouseLocationUpdated(final int x, final int y) {
		SwingUtilities.invokeLater(() -> {
            if (AssistantPanel.this.mouseX != -1 && AssistantPanel.this.mouseY != -1) {
                repaint(AssistantPanel.this.mouseX, AssistantPanel.this.mouseY, MOUSE_CURSOR_WIDTH, MOUSE_CURSOR_HEIGHT);
            }

            AssistantPanel.this.mouseX = x;
            AssistantPanel.this.mouseY = y;

            repaint(AssistantPanel.this.mouseX, AssistantPanel.this.mouseY, MOUSE_CURSOR_WIDTH, MOUSE_CURSOR_HEIGHT);
        });
	}
}

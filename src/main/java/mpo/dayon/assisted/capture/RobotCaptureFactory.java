package mpo.dayon.assisted.capture;

import java.awt.Toolkit;

import mpo.dayon.common.capture.Gray8Bits;
import mpo.dayon.common.utils.ScreenUtilities;

public class RobotCaptureFactory implements CaptureFactory {
	private static final int CAPTURE_WIDTH;

	private static final int CAPTURE_HEIGHT;

	static {
		final Toolkit toolkit = Toolkit.getDefaultToolkit();

		CAPTURE_WIDTH = toolkit.getScreenSize().width;
		CAPTURE_HEIGHT = toolkit.getScreenSize().height;
	}

	@Override
	public int getWidth() {
		return CAPTURE_WIDTH;
	}

	@Override
	public int getHeight() {
		return CAPTURE_HEIGHT;
	}

	@Override
	public byte[] captureGray(Gray8Bits quantization) {
		return ScreenUtilities.captureGray(quantization);
	}
}

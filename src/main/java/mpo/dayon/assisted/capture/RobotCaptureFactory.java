package mpo.dayon.assisted.capture;

import java.awt.*;

import mpo.dayon.common.capture.Gray8Bits;
import mpo.dayon.common.utils.ScreenUtilities;

public class RobotCaptureFactory implements CaptureFactory {
	private static final Dimension CAPTURE_DIMENSION;

	static {
		CAPTURE_DIMENSION = ScreenUtilities.SCREEN.getSize();
	}

	@Override
	public Dimension getDimension() {
		return CAPTURE_DIMENSION;
	}

	@Override
	public byte[] captureGray(Gray8Bits quantization) {
		return ScreenUtilities.captureGray(quantization);
	}
}

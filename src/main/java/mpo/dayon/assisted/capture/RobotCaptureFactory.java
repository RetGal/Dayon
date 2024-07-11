package mpo.dayon.assisted.capture;

import java.awt.*;

import mpo.dayon.common.capture.Gray8Bits;
import mpo.dayon.assisted.utils.ScreenUtilities;

public class RobotCaptureFactory implements CaptureFactory {
	private final Dimension captureDimension;

	public RobotCaptureFactory(boolean allScreens) {
		ScreenUtilities.setShareAllScreens(allScreens);
		captureDimension = ScreenUtilities.getSharedScreenSize().getSize();
	}

	@Override
	public Dimension getDimension() {
		return new Dimension(captureDimension);
	}

	@Override
	public byte[] captureScreen(Gray8Bits quantization) {
		return quantization == null ? ScreenUtilities.captureColors() : ScreenUtilities.captureGray(quantization);
	}
}

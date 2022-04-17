package mpo.dayon.assisted.capture;

import java.awt.*;

import mpo.dayon.common.capture.Gray8Bits;
import mpo.dayon.assisted.utils.ScreenUtilities;

public class RobotCaptureFactory implements CaptureFactory {
	private static Dimension captureDimension;

	public RobotCaptureFactory(boolean allScreens) {
		ScreenUtilities.setShareAllScreens(allScreens);
		captureDimension = ScreenUtilities.getSharedScreenSize().getSize();
	}

	@Override
	public Dimension getDimension() {
		return captureDimension;
	}

	@Override
	public byte[] captureGray(Gray8Bits quantization) {
		return ScreenUtilities.captureGray(quantization);
	}
}

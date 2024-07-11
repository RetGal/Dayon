package mpo.dayon.assisted.capture;

import mpo.dayon.common.capture.Gray8Bits;

import java.awt.*;

public interface CaptureFactory {

	Dimension getDimension();

	byte[] captureScreen(Gray8Bits quantization);

}

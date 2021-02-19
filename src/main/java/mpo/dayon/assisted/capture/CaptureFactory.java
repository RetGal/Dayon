package mpo.dayon.assisted.capture;

import mpo.dayon.common.capture.Gray8Bits;

import java.awt.*;

interface CaptureFactory {

	Dimension getDimension();

	byte[] captureGray(Gray8Bits quantization);

}

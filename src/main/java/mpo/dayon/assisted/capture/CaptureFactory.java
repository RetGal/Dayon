package mpo.dayon.assisted.capture;

import org.jetbrains.annotations.Nullable;

import mpo.dayon.common.capture.Gray8Bits;

import java.awt.*;

interface CaptureFactory {

	Dimension getDimension();

	@Nullable byte[] captureGray(Gray8Bits quantization);

}

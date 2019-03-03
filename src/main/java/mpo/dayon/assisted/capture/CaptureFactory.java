package mpo.dayon.assisted.capture;

import org.jetbrains.annotations.Nullable;

import mpo.dayon.common.capture.Gray8Bits;

interface CaptureFactory {
	int getWidth();

	int getHeight();

	@Nullable byte[] captureGray(Gray8Bits quantization);

}

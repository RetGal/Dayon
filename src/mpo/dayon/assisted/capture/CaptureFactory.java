package mpo.dayon.assisted.capture;

import org.jetbrains.annotations.Nullable;

import mpo.dayon.common.capture.Gray8Bits;

abstract class CaptureFactory {
	public abstract int getWidth();

	public abstract int getHeight();

	@Nullable
	public abstract byte[] captureGray(Gray8Bits quantization);

}

package mpo.dayon.assisted.capture;

import mpo.dayon.common.capture.Gray8Bits;
import org.jetbrains.annotations.Nullable;

abstract class CaptureFactory
{
    public abstract int getWidth();

    public abstract int getHeight();

    @Nullable
    public abstract byte[] captureGray(Gray8Bits quantization);

}

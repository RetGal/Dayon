package mpo.dayon.assisted.capture;

import mpo.dayon.common.capture.Gray8Bits;
import mpo.dayon.common.utils.ScreenUtilities;

import java.awt.*;

public class RobotCaptureFactory extends CaptureFactory
{
    private static final int CAPTURE_WIDTH;

    private static final int CAPTURE_HEIGHT;

    static
    {
        final Toolkit toolkit = Toolkit.getDefaultToolkit();

        CAPTURE_WIDTH = toolkit.getScreenSize().width;
        CAPTURE_HEIGHT = toolkit.getScreenSize().height;
    }

    public int getWidth()
    {
        return CAPTURE_WIDTH;
    }

    public int getHeight()
    {
        return CAPTURE_HEIGHT;
    }

    public byte[] captureGray(Gray8Bits quantization)
    {
        final byte[] pixels = ScreenUtilities.captureGray(quantization);
        return pixels;
    }
}

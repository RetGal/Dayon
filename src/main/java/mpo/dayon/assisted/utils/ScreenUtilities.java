package mpo.dayon.assisted.utils;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

import mpo.dayon.common.capture.Gray8Bits;
import mpo.dayon.common.log.Log;

import static java.lang.Math.min;
import static java.lang.String.format;

public final class ScreenUtilities {

    private static final int NUMBER_OF_SCREENS;

    private static final Rectangle COMBINED_SCREEN_SIZE;

    private static final Rectangle DEFAULT_SIZE;

    private static final Robot ROBOT;

    private static Rectangle sharedScreenSize;

    private static int[] rgb;

    private static byte[] gray;

    private static boolean shareAllScreens;

    private ScreenUtilities() {
    }

    static {
        NUMBER_OF_SCREENS = countScreens();
        DEFAULT_SIZE = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().getBounds();
        COMBINED_SCREEN_SIZE = new Rectangle(getCombinedScreenSize());
        sharedScreenSize = shareAllScreens ? COMBINED_SCREEN_SIZE : DEFAULT_SIZE;
        rgb = new int[sharedScreenSize.height * sharedScreenSize.width];
        gray = new byte[rgb.length];
        try {
            ROBOT = new Robot();
        } catch (AWTException ex) {
            throw new IllegalStateException("Could not initialize the AWT robot!", ex);
        }
    }

    public static synchronized void setShareAllScreens(boolean doShareAllScreens) {
        shareAllScreens = doShareAllScreens;
        sharedScreenSize = doShareAllScreens ? COMBINED_SCREEN_SIZE : DEFAULT_SIZE;
        rgb = new int[sharedScreenSize.height * sharedScreenSize.width];
        gray = new byte[rgb.length];
    }

    public static Rectangle getSharedScreenSize() {
        return sharedScreenSize;
    }

    public static int getNumberOfScreens() {
        return NUMBER_OF_SCREENS;
    }

    private static int countScreens() {
        return GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices().length;
    }

    private static Rectangle getCombinedScreenSize() {
        Rectangle fullSize = new Rectangle();
        GraphicsEnvironment environment = GraphicsEnvironment.getLocalGraphicsEnvironment();
        for (GraphicsDevice gd : environment.getScreenDevices()) {
            for (GraphicsConfiguration graphicsConfiguration : gd.getConfigurations()) {
                Rectangle2D.union(fullSize, graphicsConfiguration.getBounds(), fullSize);
            }
        }
        return fullSize.getBounds();
    }

    public static byte[] captureGray(Gray8Bits quantization) {
        return rgbToGray8(quantization, captureRGB(sharedScreenSize));
    }

    private static int[] captureRGB(Rectangle bounds) {
        BufferedImage image = ROBOT.createScreenCapture(bounds);
        int imageHeight = image.getHeight();
        int imageWidth = image.getWidth();
        if (imageHeight != bounds.height || imageWidth != bounds.width) {
            Log.warn(format("Image dimensions %sx%s != bound dimensions %sx%s", imageHeight, imageWidth, bounds.height, bounds.width));
            imageHeight = min(image.getHeight(), bounds.height);
            imageWidth = min(image.getWidth(), bounds.width);
        }
        int i = 0;
        for (int yPos = bounds.y; yPos < imageHeight; yPos++) {
            for (int xPos = bounds.x; xPos < imageWidth && i < rgb.length; xPos++) {
                rgb[i++] = image.getRGB(xPos, yPos);
            }
        }
        return rgb;
    }

    public static byte[] captureGray(Rectangle bounds, Gray8Bits quantization) {
        return rgbToGray8(quantization, captureRGB(bounds));
    }

    private static byte[] rgbToGray8(Gray8Bits quantization, int[] rgb) {
        return doRgbToGray8(quantization, rgb);
    }

    private static byte[] doRgbToGray8(Gray8Bits quantization, int[] rgb) {
        final byte[] xLevels = grays[quantization.ordinal()];
        int prevRgb = -1;
        byte prevGray = -1;

        for (int idx = 0; idx < rgb.length; idx++) {
            final int pixel = rgb[idx];
            if (pixel == prevRgb) {
                gray[idx] = prevGray;
                continue;
            }
            final int red = (pixel & 0x00FF0000) >> 16;
            final int green_blue = pixel & 0x0000FFFF;
            final int level = (red_levels[red] + green_blue_levels[green_blue]) >> 7;
            gray[idx] = xLevels[level];
            prevRgb = pixel;
            prevGray = gray[idx];
        }
        return gray;
    }

    private static final short[] red_levels;
    private static final short[] green_blue_levels;
    /*
     Cache the conversion from red/green/blue into gray levels.
     */
    static {
        red_levels = new short[256];
        for (int red = 0; red < 256; red++) {
            red_levels[red] = (short) (128.0 * 0.212671 * red);
        }
        green_blue_levels = new short[256 * 256];
        for (int green = 0; green < 256; green++) {
            for (int blue = 0; blue < 256; blue++) {
                green_blue_levels[(green << 8) + blue] = (short) ((128.0 * 0.715160 * green) + (128.0 * 0.072169 * blue));
            }
        }
    }

    private static final byte[][] grays;
    /*
     Cache the quantization of all the gray levels (256).
     */
    static {
        final Gray8Bits[] quantizations = Gray8Bits.values();
        grays = new byte[quantizations.length][];
        for (final Gray8Bits quantization : quantizations) {
            grays[quantization.ordinal()] = new byte[256];
            final int factor = 256 / quantization.getLevels();
            for (int idx = 0; idx < 256; idx++) {
                // DOWN (0, 32, 64, 96, ...)
                // levels[quantization.ordinal()][idx] = (byte) (idx / factor *
                // factor);
                // UP (31, 63,, 95, ...)
                grays[quantization.ordinal()][idx] = (byte) (((1 + (idx / factor)) * factor) - 1);
            }
        }
    }
}

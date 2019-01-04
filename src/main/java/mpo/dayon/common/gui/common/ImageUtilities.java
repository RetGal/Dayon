package mpo.dayon.common.gui.common;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.swing.ImageIcon;

public abstract class ImageUtilities {
	public static void writeAsJpeg(String where, String name, BufferedImage image, float compression) {
		try {
			final Iterator<ImageWriter> iter = ImageIO.getImageWritersByFormatName("jpeg");
			final ImageWriter writer = iter.next();

			final ImageOutputStream ios = ImageIO.createImageOutputStream(new File(where + name + ".jpg"));
			writer.setOutput(ios);

			final ImageWriteParam iwp = writer.getDefaultWriteParam();
			iwp.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			iwp.setCompressionQuality(compression);

			writer.write(null, new IIOImage(image, null, null), iwp);

			ios.flush();
			ios.close();
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}

	private static final Map<String, ImageIcon> cache = new HashMap<>();

	public static ImageIcon getOrCreateIcon(String name) {
		final String rname = "/images/" + name;
		ImageIcon icon = cache.get(name);
		if (icon == null) {
			try {
				icon = new ImageIcon(ImageUtilities.class.getResource(rname));
			} catch (NullPointerException ex) {
				throw new RuntimeException(String.format("Missing icon [%s].", rname));
			}
			cache.put(rname, icon);
		}
		return icon;
	}

}

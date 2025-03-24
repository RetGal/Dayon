package mpo.dayon.common.gui.common;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import java.awt.*;
import java.awt.image.BaseMultiResolutionImage;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

import static java.lang.String.format;

public final class ImageUtilities {
    private ImageUtilities() {
    }

    private static final Map<String, ImageIcon> ICON_CACHE = new HashMap<>();

    public static ImageIcon getOrCreateIcon(String name) {
        return ICON_CACHE.computeIfAbsent(name, key -> {
            final String rname = "/images/%s/" + name;
            try {
                return key.equals("waiting.gif")
                        ? new ImageIcon(ImageUtilities.class.getResource("/images/" + name))
                        : new ImageIcon(new BaseMultiResolutionImage(getImages(format(rname, "100", "125", "150", "175", "200")).toArray(new Image[0])));
            } catch (IllegalArgumentException ex) {
                throw new IllegalStateException(format("Missing icon [%s].", rname));
            }
        });
    }

    private static List<Image> getImages(String... paths) {
        return Arrays.stream(paths).map(path -> {
            try {
                return ImageIO.read(ImageUtilities.class.getResource(path));
            } catch (IOException ex) {
                throw new IllegalArgumentException(ex);
            }
        }).collect(Collectors.toList());
    }

}

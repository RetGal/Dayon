package mpo.dayon.common.gui.common;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import java.awt.*;
import java.awt.image.BaseMultiResolutionImage;
import java.io.IOException;
import java.util.*;
import java.util.List;

import static java.lang.String.format;

public final class ImageUtilities {
    private ImageUtilities() {
    }

    private static final Map<String, ImageIcon> ICON_CACHE = new HashMap<>();

    public static ImageIcon getOrCreateIcon(String name) {
        ImageIcon icon = ICON_CACHE.get(name);
        if (icon == null) {
            final String rname = "/images/%s/" + name;
            try {
                if (!name.equals("waiting.gif")) {
                    List<Image> imgList = new ArrayList<>();
                    for (String s : Arrays.asList("100", "125", "150", "175", "200")) {
                        imgList.add(ImageIO.read(ImageUtilities.class.getResource(format(rname, s))));
                    }
                    BaseMultiResolutionImage multiResolutionImage = new BaseMultiResolutionImage(imgList.toArray(new Image[0]));
                    icon = new ImageIcon(multiResolutionImage);
                } else {
                    icon = new ImageIcon(ImageUtilities.class.getResource("/images/" + name));
                }
            } catch (IOException | IllegalArgumentException ex) {
                throw new IllegalStateException(format("Missing icon [%s].", rname));
            }
            ICON_CACHE.put(name, icon);
        }
        return icon;
    }

}

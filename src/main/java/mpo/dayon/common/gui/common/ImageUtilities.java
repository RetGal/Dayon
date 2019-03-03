package mpo.dayon.common.gui.common;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

public abstract class ImageUtilities {
    private ImageUtilities() {
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

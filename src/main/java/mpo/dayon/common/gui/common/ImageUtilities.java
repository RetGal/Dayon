package mpo.dayon.common.gui.common;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import java.util.HashMap;
import java.util.Map;

public abstract class ImageUtilities {
    private ImageUtilities() {
    }

    private static final Map<String, Icon> FIXED_SCALE_ICON_CACHE = new HashMap<>();
    private static final Map<String, ImageIcon> ICON_CACHE = new HashMap<>();

    public static Icon getOrCreateFixedScaleIcon(String name) {
        // workaround for HiDPI support introduced in Java 9, causing ugly scaled icons
        if (Float.parseFloat(System.getProperty("java.class.version")) < 53.0) {
            return getOrCreateIcon(name);
        }
        final String rname = "/images/" + name;
        Icon icon = FIXED_SCALE_ICON_CACHE.get(name);
        if (icon == null) {
            try {
                icon = new FixedScaleIcon(new ImageIcon(ImageUtilities.class.getResource(rname)));
            } catch (NullPointerException ex) {
                throw new IllegalStateException(String.format("Missing icon [%s].", rname));
            }
            FIXED_SCALE_ICON_CACHE.put(rname, icon);
        }
        return icon;
    }

    public static ImageIcon getOrCreateIcon(String name) {
        final String rname = "/images/" + name;
        ImageIcon icon = ICON_CACHE.get(name);
        if (icon == null) {
            try {
                icon = new ImageIcon(ImageUtilities.class.getResource(rname));
            } catch (NullPointerException ex) {
                throw new IllegalStateException(String.format("Missing icon [%s].", rname));
            }
            ICON_CACHE.put(rname, icon);
        }
        return icon;
    }

}

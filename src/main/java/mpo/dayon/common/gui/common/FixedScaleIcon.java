package mpo.dayon.common.gui.common;


import javax.swing.Icon;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;

public class FixedScaleIcon implements Icon {
    private final Icon icon;

    public FixedScaleIcon(Icon icon)
    {
        this.icon = icon;
    }

    @Override
    public int getIconWidth()
    {
        return icon.getIconWidth();
    }

    @Override
    public int getIconHeight()
    {
        return icon.getIconHeight();
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y)
    {
        Graphics2D g2d = (Graphics2D)g.create();

        AffineTransform at = g2d.getTransform();

        int scaleX = (int) (x * at.getScaleX());
        int scaleY = (int) (y * at.getScaleY());

        int offsetX = (int) (icon.getIconWidth() * (at.getScaleX() - 1) / 2);
        int offsetY = (int) (icon.getIconHeight() * (at.getScaleY() - 1) / 2);

        int locationX = scaleX + offsetX;
        int locationY = scaleY + offsetY;

        at.concatenate(AffineTransform.getScaleInstance(1 / at.getScaleX(), 1 / at.getScaleY()));
        g2d.setTransform(at);

        icon.paintIcon(c, g2d, locationX, locationY);

        g2d.dispose();
    }

}

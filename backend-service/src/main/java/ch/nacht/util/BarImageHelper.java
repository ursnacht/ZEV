package ch.nacht.util;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public class BarImageHelper {

    private static final Color TRACK_COLOR = new Color(0xEE, 0xEE, 0xEE);

    private BarImageHelper() {
    }

    public static BufferedImage createBar(Double value, Double max, int maxWidth, int height, Color barColor) {
        BufferedImage img = new BufferedImage(maxWidth, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(TRACK_COLOR);
        g.fillRect(0, 0, maxWidth, height);
        if (max != null && max > 0 && value != null && value > 0) {
            int barWidth = Math.max(1, (int) (value / max * maxWidth));
            g.setColor(barColor);
            g.fillRect(0, 0, barWidth, height);
        }
        g.dispose();
        return img;
    }
}

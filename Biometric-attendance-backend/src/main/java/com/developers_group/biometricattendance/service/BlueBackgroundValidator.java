package com.developers_group.biometricattendance.service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Iterator;

/**
 * Checks that the BACKGROUND of a profile photo is blue.
 *
 * It does not count blue pixels in the whole picture (a blue shirt would pass).
 * Instead it looks only at the places where the background is in a normal
 * portrait: a band across the top and a band down the left and right edges
 * (down to about two thirds of the height, so the shoulders and clothes at the
 * bottom corners are left out). Those border areas must be mostly blue, and the
 * two top corners must each be mostly blue as well.
 *
 * A pixel is "blue" when its hue is between 185 and 255 degrees, it is clearly
 * coloured (saturation) and not almost black (brightness). That accepts sky blue,
 * royal blue and navy and rejects grey, white, green, red, skin and hair tones.
 *
 * Limitations (also documented to the user): this is a colour check of the border
 * area, not a person/background segmentation. A photo that is heavily cropped so
 * that the head or clothes fill the border, or a blue wall that is not behind the
 * person, can still pass or fail unexpectedly. No dependency is needed: it uses the
 * JDK's own ImageIO (JPEG, PNG, GIF).
 */
public final class BlueBackgroundValidator {

    public static final String MESSAGE =
            "Invalid photo. Please upload a profile picture with a blue background.";

    /** Pictures are shrunk to this many pixels on the longest side before they are inspected. */
    private static final int ANALYSIS_SIZE = 200;

    /** Refuse absurd dimensions before decoding (decompression-bomb guard). */
    private static final long MAX_PIXELS = 40_000_000L;

    private static final double HUE_MIN = 185.0;
    private static final double HUE_MAX = 255.0;
    private static final double MIN_SATURATION = 0.30;
    private static final double MIN_BRIGHTNESS = 0.20;

    /** Share of the border area that must be blue. */
    private static final double REQUIRED_BORDER_SHARE = 0.75;

    /** Share of each top corner that must be blue. */
    private static final double REQUIRED_CORNER_SHARE = 0.60;

    public enum Result { BLUE_BACKGROUND, NOT_BLUE, UNREADABLE }

    private BlueBackgroundValidator() {
    }

    public static Result check(byte[] imageBytes) {

        BufferedImage source;

        try {
            if (!hasReasonableSize(imageBytes)) {
                return Result.UNREADABLE;
            }

            source = ImageIO.read(new ByteArrayInputStream(imageBytes));
        } catch (Exception | OutOfMemoryError e) {
            return Result.UNREADABLE;
        }

        if (source == null || source.getWidth() < 1 || source.getHeight() < 1) {
            return Result.UNREADABLE;
        }

        BufferedImage image = shrinkOnWhite(source);

        return isBlueBackground(image) ? Result.BLUE_BACKGROUND : Result.NOT_BLUE;
    }

    // Visible for testing
    static boolean isBlueBackground(BufferedImage image) {

        int w = image.getWidth();
        int h = image.getHeight();

        int topBand = Math.max(1, (int) Math.round(h * 0.20));
        int sideBand = Math.max(1, (int) Math.round(w * 0.12));
        int sideDepth = Math.max(topBand, (int) Math.round(h * 0.65));

        long total = 0;
        long blue = 0;

        for (int y = 0; y < sideDepth; y++) {
            for (int x = 0; x < w; x++) {

                boolean inTop = y < topBand;
                boolean inSide = x < sideBand || x >= w - sideBand;

                if (!inTop && !inSide) continue;

                total++;
                if (isBlue(image.getRGB(x, y))) blue++;
            }
        }

        if (total == 0 || blue < total * REQUIRED_BORDER_SHARE) {
            return false;
        }

        int cornerW = Math.max(1, (int) Math.round(w * 0.15));
        int cornerH = Math.max(1, (int) Math.round(h * 0.15));

        return blueShare(image, 0, 0, cornerW, cornerH) >= REQUIRED_CORNER_SHARE
                && blueShare(image, w - cornerW, 0, cornerW, cornerH) >= REQUIRED_CORNER_SHARE;
    }

    private static double blueShare(BufferedImage image, int x0, int y0, int width, int height) {

        long total = 0;
        long blue = 0;

        for (int y = y0; y < y0 + height; y++) {
            for (int x = x0; x < x0 + width; x++) {
                total++;
                if (isBlue(image.getRGB(x, y))) blue++;
            }
        }

        return total == 0 ? 0 : (double) blue / total;
    }

    // Visible for testing
    static boolean isBlue(int rgb) {

        float[] hsb = Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);

        double hue = hsb[0] * 360.0;

        return hue >= HUE_MIN && hue <= HUE_MAX
                && hsb[1] >= MIN_SATURATION
                && hsb[2] >= MIN_BRIGHTNESS;
    }

    /** Reads only the header to learn the size, so a huge image is refused before it is decoded. */
    private static boolean hasReasonableSize(byte[] bytes) throws java.io.IOException {

        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {

            if (in == null) return false;

            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);

            if (!readers.hasNext()) return false;

            ImageReader reader = readers.next();

            try {
                reader.setInput(in);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                return pixels > 0 && pixels <= MAX_PIXELS;
            } finally {
                reader.dispose();
            }
        }
    }

    /** Scales down and flattens onto white, so transparent areas count as white (not blue). */
    private static BufferedImage shrinkOnWhite(BufferedImage source) {

        double scale = Math.min(1.0, (double) ANALYSIS_SIZE / Math.max(source.getWidth(), source.getHeight()));

        int w = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(source.getHeight() * scale));

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();

        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
            g.drawImage(source, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }

        return out;
    }
}

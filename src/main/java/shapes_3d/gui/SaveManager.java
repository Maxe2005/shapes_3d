package shapes_3d.gui;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Small utility class to centralize saving of scene text and images.
 */
public final class SaveManager {

    private SaveManager() {}

    public static void saveSceneText(Path dest, String content) throws IOException {
        if (dest == null) throw new IllegalArgumentException("dest is null");
        // ensure parent exists
        if (dest.getParent() != null) Files.createDirectories(dest.getParent());
        Files.writeString(dest, content);
    }

    public static void saveImage(Image fxImage, Path dest) throws IOException {
        if (fxImage == null) throw new IllegalArgumentException("image is null");
        if (dest == null) throw new IllegalArgumentException("dest is null");
        BufferedImage bimg = SwingFXUtils.fromFXImage(fxImage, null);

        // flip vertically (top<->bottom) to match renderer coordinate system
        int w = bimg.getWidth();
        int h = bimg.getHeight();
        BufferedImage flipped = new BufferedImage(w, h, bimg.getType() == 0 ? BufferedImage.TYPE_INT_ARGB : bimg.getType());
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = bimg.getRGB(x, y);
                flipped.setRGB(x, h - 1 - y, rgb);
            }
        }

        String name = dest.getFileName().toString();
        String lower = name.toLowerCase();
        String fmt = "png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) fmt = "jpg";
        // If no extension provided, default to .png and adjust dest
        if (!lower.contains(".")) {
            dest = dest.resolveSibling(name + ".png");
        }

        // ensure parent exists
        if (dest.getParent() != null) Files.createDirectories(dest.getParent());
        ImageIO.write(flipped, fmt, dest.toFile());
    }
}

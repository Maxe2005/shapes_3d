package shapes_3d.gui;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

import java.awt.image.BufferedImage;

/**
 * Petite classe UI qui encapsule l'ImageView et le WritableImage.
 */
public class ImagePane {

    private final ImageView imageView;
    private WritableImage canvasImage;

    public ImagePane() {
        imageView = new ImageView();
        imageView.setPreserveRatio(true);
        imageView.setScaleY(-1);
        imageView.setSmooth(true);
    }

    public ImageView getImageView() {
        return imageView;
    }

    public void createCanvas(int width, int height) {
        this.canvasImage = new WritableImage(width, height);
        imageView.setImage(canvasImage);
    }

    public void applyBufferedPart(BufferedImage part, int startX, int startY) {
        if (canvasImage == null) return;
        PixelWriter pw = canvasImage.getPixelWriter();
        for (int y = 0; y < part.getHeight(); y++) {
            for (int x = 0; x < part.getWidth(); x++) {
                int argb = part.getRGB(x, y);
                pw.setArgb(startX + x, startY + y, argb);
            }
        }
        imageView.setImage(canvasImage);
    }

    public void setImageFromBuffered(BufferedImage img) {
        Image fx = SwingFXUtils.toFXImage(img, null);
        imageView.setImage(fx);
    }

}

package shapes_3d.gui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.image.PixelWriter;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import ray_tracer.parsing.SceneFileParser;
import ray_tracer.parsing.Camera;
import ray_tracer.geometry.Vector;
import ray_tracer.geometry.Point;
import ray_tracer.renderer.DefaultRenderer;
import ray_tracer.renderer.RenderOptions;
import ray_tracer.renderer.RenderTask;
import ray_tracer.renderer.ProgressListener;
import ray_tracer.renderer.RenderUpdate;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FXMain extends Application {

    private ImageView imageView;
    private WritableImage canvasImage;
    private ray_tracer.parsing.Scene currentScene;
    private DefaultRenderer renderer;
    private RenderTask currentTask;
    private ExecutorService exec = Executors.newSingleThreadExecutor();
    private int width = 800;
    private int height = 600;

    @Override
    public void start(Stage stage) {
        renderer = new DefaultRenderer(Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()));

        BorderPane root = new BorderPane();
        imageView = new ImageView();
        imageView.setFitWidth(width);
        imageView.setFitHeight(height);
        imageView.setPreserveRatio(true);
        imageView.setScaleY(-1);

        canvasImage = new WritableImage(width, height);
        imageView.setImage(canvasImage);

        Button loadBtn = new Button("Charger scène...");
        loadBtn.setFocusTraversable(false);
        loadBtn.setOnAction(ev -> onLoadScene(stage));

        root.setTop(loadBtn);
        root.setCenter(imageView);

        Scene fxScene = new Scene(root, width, height + 40);

        fxScene.setOnKeyPressed(this::onKeyPressed);

        stage.setTitle("RayTracer - Visualisation interactive (prototype)");
        stage.setScene(fxScene);
        stage.show();
    }

    private void onLoadScene(Stage stage) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Ouvrir fichier de scène");
        File f = chooser.showOpenDialog(stage);
        if (f == null) return;
        try {
            currentScene = SceneFileParser.parse(f.getAbsolutePath());
            // ensure output file
            currentScene.setOutputFile("output.png");
            // set default camera size
            this.width = Math.max(200, currentScene.getWidth());
            this.height = Math.max(200, currentScene.getHeight());
            canvasImage = new WritableImage(this.width, this.height);
            imageView.setImage(canvasImage);
            startRender(true);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void onKeyPressed(javafx.scene.input.KeyEvent ev) {
        if (currentScene == null) return;
        Camera cam = currentScene.getCamera();
        
        // Spherical coordinates movement
        Point lookFrom = cam.getLookFrom();
        Point lookAt = cam.getLookAt();
        
        // Vector from LookAt to LookFrom
        Vector v = lookFrom.subtraction(lookAt);
        
        double r = v.norm();
        // theta: angle from Y axis (0 to PI)
        double theta = Math.acos(v.getY() / r);
        // phi: angle in XZ plane
        double phi = Math.atan2(v.getZ(), v.getX());
        
        double angleStep = Math.toRadians(5);
        double zoomStep = r * 0.1;
        
        KeyCode code = ev.getCode();
        boolean changed = false;
        
        if (code == KeyCode.LEFT) {
            phi += angleStep;
            changed = true;
        } else if (code == KeyCode.RIGHT) {
            phi -= angleStep;
            changed = true;
        } else if (code == KeyCode.UP) {
            theta -= angleStep;
            changed = true;
        } else if (code == KeyCode.DOWN) {
            theta += angleStep;
            changed = true;
        } else if (code == KeyCode.A) {
            r -= zoomStep;
            if (r < 0.1) r = 0.1;
            changed = true;
        } else if (code == KeyCode.R) {
            r += zoomStep;
            changed = true;
        }
        
        if (changed) {
            // Clamp theta to avoid gimbal lock
            double epsilon = 0.01;
            if (theta < epsilon) theta = epsilon;
            if (theta > Math.PI - epsilon) theta = Math.PI - epsilon;
            
            // Convert back to Cartesian
            double x = r * Math.sin(theta) * Math.cos(phi);
            double y = r * Math.cos(theta);
            double z = r * Math.sin(theta) * Math.sin(phi);
            
            Vector newOffset = new Vector(x, y, z);
            Point newLookFrom = (Point) lookAt.addition(newOffset);
            
            // Recalculate Up vector to keep horizon level
            Vector forward = lookAt.subtraction(newLookFrom).normalize();
            Vector worldUp = new Vector(0, 1, 0);
            Vector right = forward.vectorialProduct(worldUp).normalize();
            Vector newUp = right.vectorialProduct(forward).normalize();
            
            Camera newCam = new Camera(
                newLookFrom.getX(), newLookFrom.getY(), newLookFrom.getZ(),
                lookAt.getX(), lookAt.getY(), lookAt.getZ(),
                newUp.getX(), newUp.getY(), newUp.getZ(),
                cam.getFov()
            );
            
            currentScene.setCamera(newCam);
            startRender(true);
        }
    }

    private synchronized void startRender(boolean lowRes) {
        // cancel previous
        try {
            if (currentTask != null && !currentTask.isDone()) currentTask.cancel();
        } catch (Exception ignored) {}

        RenderOptions opts = new RenderOptions();
        opts.samplesPerPixel = lowRes ? 1 : 10;
        opts.maxDepth = 5;
        opts.tileSize = 64;
        opts.threadCount = Runtime.getRuntime().availableProcessors();
        opts.lowResFactor = lowRes ? 0.4 : 1.0;
        opts.progressive = false;

        // reset canvas
        canvasImage = new WritableImage(width, height);
        imageView.setImage(canvasImage);

        currentTask = renderer.render(currentScene, currentScene.getCamera(), width, height, opts);
        currentTask.addProgressListener(new ProgressListener() {
            @Override
            public void onUpdate(RenderUpdate update) {
                Platform.runLater(() -> applyUpdate(update));
            }
        });

        // wait for final image in background
        exec.execute(() -> {
            try {
                BufferedImage finalImg = currentTask.getFuture().get();
                Platform.runLater(() -> imageView.setImage(SwingFXUtils.toFXImage(finalImg, null)));
            } catch (Exception e) {
                // cancelled or failed
            }
        });
    }

    private void applyUpdate(RenderUpdate update) {
        BufferedImage part = update.imagePart;
        int startX = update.x;
        int startY = update.y;
        PixelWriter pw = canvasImage.getPixelWriter();
        for (int y = 0; y < part.getHeight(); y++) {
            for (int x = 0; x < part.getWidth(); x++) {
                int argb = part.getRGB(x, y);
                pw.setArgb(startX + x, startY + y, argb);
            }
        }
        imageView.setImage(canvasImage);
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        try { if (currentTask != null) currentTask.cancel(); } catch (Exception ignored) {}
        if (renderer != null) {
            // nothing to close in DefaultRenderer API
        }
        exec.shutdownNow();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

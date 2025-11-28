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

        canvasImage = new WritableImage(width, height);
        imageView.setImage(canvasImage);

        Button loadBtn = new Button("Charger scène...");
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
        double step = 0.5;
        KeyCode code = ev.getCode();
        try {
            if (code == KeyCode.LEFT) {
                cam = cam.copy().translate(new Vector(-step, 0, 0));
            } else if (code == KeyCode.RIGHT) {
                cam = cam.copy().translate(new Vector(step, 0, 0));
            } else if (code == KeyCode.UP) {
                cam = cam.copy().translate(new Vector(0, 0, -step));
            } else if (code == KeyCode.DOWN) {
                cam = cam.copy().translate(new Vector(0, 0, step));
            } else if (code == KeyCode.Q) {
                cam = cam.copy().translate(new Vector(0, step, 0));
            } else if (code == KeyCode.E) {
                cam = cam.copy().translate(new Vector(0, -step, 0));
            } else {
                return;
            }
            currentScene.setCamera(cam);
            startRender(true);
        } catch (Exception ex) {
            ex.printStackTrace();
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

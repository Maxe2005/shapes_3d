package shapes_3d.gui;

import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import ray_tracer.parsing.SceneFileParser;
import ray_tracer.parsing.Camera;
import ray_tracer.geometry.Point;
import ray_tracer.geometry.Vector;
import ray_tracer.renderer.DefaultRenderer;
import ray_tracer.renderer.ProgressListener;
import ray_tracer.renderer.RenderOptions;
import ray_tracer.renderer.RenderTask;
import ray_tracer.renderer.RenderUpdate;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Controller that builds the UI and contains the logic previously in FXMain.
 * Splitting this into a separate class keeps the Application class small.
 */
public class GuiController {

    private ImageView imageView;
    private WritableImage canvasImage;
    private ray_tracer.parsing.Scene currentScene;
    private DefaultRenderer renderer;
    private RenderTask currentTask;
    private ExecutorService exec = Executors.newSingleThreadExecutor();
    private int width = 800;
    private int height = 600;
    private File originalSceneFile;
    private String originalSceneContent;
    private TextArea sourceEditor;
    private Button applyBtn;
    private Button revertBtn;
    private Button insertCameraBtn;
    private Button saveSceneBtn;
    private Button saveImageBtn;

    public void init(Stage stage) {
        renderer = new DefaultRenderer(Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()));

        BorderPane root = new BorderPane();
        imageView = new ImageView();
        imageView.setPreserveRatio(true);
        imageView.setScaleY(-1);

        canvasImage = new WritableImage(width, height);
        imageView.setImage(canvasImage);

        StackPane imageBox = new StackPane(imageView);
        imageBox.setPrefSize(Double.MAX_VALUE, Double.MAX_VALUE);
        StackPane.setMargin(imageView, javafx.geometry.Insets.EMPTY);
        VBox.setVgrow(imageBox, Priority.ALWAYS);

        Button loadBtn = new Button("Charger scène...");
        loadBtn.setFocusTraversable(false);
        loadBtn.setOnAction(ev -> onLoadScene(stage));

        // Editor controls
        applyBtn = new Button("Appliquer (aperçu)");
        applyBtn.setFocusTraversable(false);
        applyBtn.setOnAction(ev -> onApplyEditorChanges());
        applyBtn.setDisable(true);

        revertBtn = new Button("Réinitialiser");
        revertBtn.setFocusTraversable(false);
        revertBtn.setOnAction(ev -> onRevertToOriginal());
        revertBtn.setDisable(true);

        insertCameraBtn = new Button("Insérer caméra");
        insertCameraBtn.setFocusTraversable(false);
        insertCameraBtn.setOnAction(ev -> onInsertCameraToEditor());
        insertCameraBtn.setDisable(true);

        saveSceneBtn = new Button("Enregistrer scène");
        saveSceneBtn.setFocusTraversable(false);
        saveSceneBtn.setOnAction(ev -> onSaveScene());
        saveSceneBtn.setDisable(true);

        saveImageBtn = new Button("Enregistrer image");
        saveImageBtn.setFocusTraversable(false);
        saveImageBtn.setOnAction(ev -> onSaveImage());
        saveImageBtn.setDisable(false);

        HBox topBar = new HBox(8, loadBtn, applyBtn, revertBtn, insertCameraBtn, saveSceneBtn, saveImageBtn);

        // TabPane with Image view and Source editor
        TabPane tabPane = new TabPane();
        Tab imageTab = new Tab("Image");
        imageTab.setContent(imageBox);
        imageTab.setClosable(false);

        sourceEditor = new TextArea();
        sourceEditor.setWrapText(false);
        sourceEditor.setDisable(true);
        Tab sourceTab = new Tab("Source (.scene)");
        VBox sourceBox = new VBox(sourceEditor);
        sourceBox.setPrefSize(Double.MAX_VALUE, Double.MAX_VALUE);
        VBox.setVgrow(sourceEditor, Priority.ALWAYS);
        sourceTab.setContent(sourceBox);
        sourceTab.setClosable(false);

        tabPane.getTabs().addAll(imageTab, sourceTab);

        root.setTop(topBar);
        root.setCenter(tabPane);

        Scene fxScene = new Scene(root, width, height + 80);

        // Capture key events at scene level so TabPane doesn't consume arrow keys
        fxScene.addEventFilter(KeyEvent.KEY_PRESSED, ev -> {
            KeyCode code = ev.getCode();
            if (code == KeyCode.LEFT || code == KeyCode.RIGHT || code == KeyCode.UP || code == KeyCode.DOWN || code == KeyCode.A || code == KeyCode.R) {
                onKeyPressed(ev);
                ev.consume();
            }
        });

        stage.setTitle("RayTracer - Visualisation interactive (prototype)");
        stage.setScene(fxScene);
        stage.setOnCloseRequest(ev -> {
            try { if (currentTask != null && !currentTask.isDone()) currentTask.cancel(); } catch (Exception ignored) {}
            try { exec.shutdownNow(); } catch (Exception ignored) {}
            Platform.exit();
            System.exit(0);
        });
        stage.show();
    }

    private void onInsertCameraToEditor() {
        if (currentScene == null || sourceEditor == null) return;
        try {
            Camera cam = currentScene.getCamera();
            if (cam == null) return;
            Point lf = cam.getLookFrom();
            Point la = cam.getLookAt();
            double upx = 0, upy = 1, upz = 0;
            try {
                java.lang.reflect.Method m = cam.getClass().getMethod("getUp");
                Object up = m.invoke(cam);
                if (up instanceof Vector) {
                    Vector vup = (Vector) up;
                    upx = vup.getX(); upy = vup.getY(); upz = vup.getZ();
                }
            } catch (Exception ignored) {}
            double fov = cam.getFov();

            String cameraLine = String.format(java.util.Locale.US, "camera %.6f %.6f %.6f %.6f %.6f %.6f %.6f %.6f %.6f %.6f",
                    lf.getX(), lf.getY(), lf.getZ(),
                    la.getX(), la.getY(), la.getZ(),
                    upx, upy, upz,
                    fov);

            String text = sourceEditor.getText();
            String[] lines = text == null ? new String[0] : text.split("\r?\n", -1);
            boolean replaced = false;
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].trim().startsWith("camera ")) {
                    lines[i] = cameraLine;
                    replaced = true;
                    break;
                }
            }
            String newText;
            if (replaced) {
                newText = String.join(System.lineSeparator(), lines);
            } else {
                int insertIndex = 0;
                if (lines.length > 0 && lines[0].trim().startsWith("size")) insertIndex = 1;
                java.util.List<String> list = new java.util.ArrayList<>();
                for (int i = 0; i < insertIndex; i++) list.add(lines[i]);
                list.add(cameraLine);
                for (int i = insertIndex; i < lines.length; i++) list.add(lines[i]);
                newText = String.join(System.lineSeparator(), list);
            }
            sourceEditor.setText(newText);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void onLoadScene(Stage stage) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Ouvrir fichier de scène");
        File f = chooser.showOpenDialog(stage);
        if (f == null) return;
        try {
            currentScene = SceneFileParser.parse(f.getAbsolutePath());
            originalSceneFile = f;
            try {
                originalSceneContent = Files.readString(f.toPath(), StandardCharsets.UTF_8);
                sourceEditor.setText(originalSceneContent);
            } catch (IOException ioe) {
                originalSceneContent = null;
                sourceEditor.setText("");
            }
            currentScene.setOutputFile("output.png");
            this.width = Math.max(200, currentScene.getWidth());
            this.height = Math.max(200, currentScene.getHeight());
            canvasImage = new WritableImage(this.width, this.height);
            imageView.setImage(canvasImage);
            startRender(true);
            try {
                sourceEditor.setDisable(false);
                applyBtn.setDisable(false);
                revertBtn.setDisable(false);
                insertCameraBtn.setDisable(false);
                saveSceneBtn.setDisable(false);
            } catch (Exception ignore) {}
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // --- Save handlers -------------------------------------------------
    private void onSaveScene() {
        if (sourceEditor == null) return;
        String content = sourceEditor.getText();
        if (content == null) return;

        // If we have an original file, ask overwrite or save as
        if (originalSceneFile != null) {
            Alert a = new Alert(Alert.AlertType.CONFIRMATION);
            a.setTitle("Enregistrer la scène");
            a.setHeaderText("Enregistrer les modifications de la scène");
            a.setContentText("Choisissez : écraser le fichier original, enregistrer sous un nouveau nom, ou annuler.");
            ButtonType overwrite = new ButtonType("Écraser");
            ButtonType saveAs = new ButtonType("Enregistrer sous...");
            ButtonType cancel = new ButtonType("Annuler", ButtonBar.ButtonData.CANCEL_CLOSE);
            a.getButtonTypes().setAll(overwrite, saveAs, cancel);
            a.showAndWait().ifPresent(choice -> {
                if (choice == overwrite) {
                    try {
                        SaveManager.saveSceneText(originalSceneFile.toPath(), content);
                    } catch (IOException e) {
                        showError("Erreur lors de l'enregistrement : " + e.getMessage());
                    }
                } else if (choice == saveAs) {
                    FileChooser chooser = new FileChooser();
                    chooser.setTitle("Enregistrer la scène sous...");
                    chooser.setInitialFileName(originalSceneFile.getName());
                    File dest = chooser.showSaveDialog(imageView.getScene().getWindow());
                    if (dest != null) {
                        try {
                            SaveManager.saveSceneText(dest.toPath(), content);
                        } catch (IOException e) {
                            showError("Erreur lors de l'enregistrement : " + e.getMessage());
                        }
                    }
                }
            });
        } else {
            // No original file: prompt save as
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Enregistrer la scène");
            File dest = chooser.showSaveDialog(imageView.getScene().getWindow());
            if (dest != null) {
                try {
                    SaveManager.saveSceneText(dest.toPath(), content);
                    originalSceneFile = dest;
                } catch (IOException e) {
                    showError("Erreur lors de l'enregistrement : " + e.getMessage());
                }
            }
        }
    }

    private void onSaveImage() {
        javafx.scene.image.Image img = imageView.getImage();
        if (img == null) {
            showError("Aucune image à enregistrer.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Enregistrer l'image");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("PNG image", "*.png"),
                new FileChooser.ExtensionFilter("JPEG image", "*.jpg", "*.jpeg")
        );
        File dest = chooser.showSaveDialog(imageView.getScene().getWindow());
        if (dest != null) {
            try {
                SaveManager.saveImage(img, dest.toPath());
            } catch (IOException e) {
                showError("Erreur lors de l'enregistrement de l'image : " + e.getMessage());
            }
        }
    }

    private void showError(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle("Erreur");
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    private void onApplyEditorChanges() {
        if (sourceEditor == null) return;
        String edited = sourceEditor.getText();
        if (edited == null) return;
        Path tmp = null;
        try {
            tmp = Files.createTempFile("scene_preview", ".scene");
            Files.writeString(tmp, edited, StandardCharsets.UTF_8);
            ray_tracer.parsing.Scene preview = SceneFileParser.parse(tmp.toAbsolutePath().toString());
            if (preview != null) {
                preview.setOutputFile("output_preview.png");
                this.width = Math.max(200, preview.getWidth());
                this.height = Math.max(200, preview.getHeight());
                canvasImage = new WritableImage(this.width, this.height);
                imageView.setImage(canvasImage);
                currentScene = preview;
                startRender(true);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (tmp != null) {
                try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
            }
        }
    }

    private void onRevertToOriginal() {
        if (originalSceneFile == null) return;
        try {
            currentScene = SceneFileParser.parse(originalSceneFile.getAbsolutePath());
            if (originalSceneContent != null) sourceEditor.setText(originalSceneContent);
            this.width = Math.max(200, currentScene.getWidth());
            this.height = Math.max(200, currentScene.getHeight());
            canvasImage = new WritableImage(this.width, this.height);
            imageView.setImage(canvasImage);
            startRender(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void onKeyPressed(javafx.scene.input.KeyEvent ev) {
        if (currentScene == null) return;
        Camera cam = currentScene.getCamera();

        Point lookFrom = cam.getLookFrom();
        Point lookAt = cam.getLookAt();
        Vector v = lookFrom.subtraction(lookAt);

        double r = v.norm();
        double theta = Math.acos(v.getY() / r);
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
            double epsilon = 0.01;
            if (theta < epsilon) theta = epsilon;
            if (theta > Math.PI - epsilon) theta = Math.PI - epsilon;

            double x = r * Math.sin(theta) * Math.cos(phi);
            double y = r * Math.cos(theta);
            double z = r * Math.sin(theta) * Math.sin(phi);

            Vector newOffset = new Vector(x, y, z);
            Point newLookFrom = (Point) lookAt.addition(newOffset);

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

        canvasImage = new WritableImage(width, height);
        imageView.setImage(canvasImage);

        currentTask = renderer.render(currentScene, currentScene.getCamera(), width, height, opts);
        currentTask.addProgressListener(new ProgressListener() {
            @Override
            public void onUpdate(RenderUpdate update) {
                Platform.runLater(() -> applyUpdate(update));
            }
        });

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

}

package shapes_3d.gui;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.ListView;
import javafx.scene.control.ListCell;
import javafx.scene.image.ImageView;
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
import ray_tracer.renderer.ProgressListener;
import ray_tracer.renderer.RenderOptions;
import ray_tracer.renderer.RenderUpdate;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import shapes_3d.renderer.RenderService;

/**
 * Controller that builds the UI and contains the logic previously in FXMain.
 * Splitting this into a separate class keeps the Application class small.
 */
public class GuiController {

    private ImageView imageView;
    private ray_tracer.parsing.Scene currentScene;
    private RenderService renderService;
    private ImagePane imagePane;
    private CameraController cameraController = new CameraController();
    private int width = 800;
    private int height = 600;
    private File originalSceneFile;
    private String originalSceneContent;
    private SceneTextEditor sourceEditor;
    private boolean sourceTabViewed = false;
    private Button applyBtn;
    private Button revertBtn;
    private Button insertCameraBtn;
    private Button saveSceneBtn;
    private Button saveImageBtn;
    private TabPane tabPane;
    private Tab imageTab;
    private Tab sourceTab;
    private Tab warningsTab;
    private ListView<String> warningsList;
    private ParserIssuesController parserIssuesController;

    public void init(Stage stage) {
        renderService = new RenderService();
        imagePane = new ImagePane();

        BorderPane root = new BorderPane();
        imageView = imagePane.getImageView();
        imagePane.createCanvas(width, height);

        StackPane imageBox = new StackPane(imageView);
        // Allow the imageBox to shrink below the image intrinsic size
        // to avoid a circular sizing dependency (StackPane sizing from child).
        imageBox.setMinSize(0, 0);
        imageBox.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        imageBox.setPrefSize(Double.MAX_VALUE, Double.MAX_VALUE);
        StackPane.setMargin(imageView, javafx.geometry.Insets.EMPTY);
        VBox.setVgrow(imageBox, Priority.ALWAYS);

        // Make the ImageView resize to the available area while preserving the
        // original image aspect ratio. Binding fitWidth/fitHeight to the
        // container ensures the image is always contained and fills space.
        imageView.setSmooth(true);
        imageView.fitWidthProperty().bind(imageBox.widthProperty());
        imageView.fitHeightProperty().bind(imageBox.heightProperty());

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
        // No scene loaded yet -> disable image saving
        saveImageBtn.setDisable(true);

        HBox topBar = new HBox(8, loadBtn, applyBtn, revertBtn, insertCameraBtn, saveSceneBtn, saveImageBtn);

        // TabPane with Image view, Source editor and Warnings
        tabPane = new TabPane();
        imageTab = new Tab("Image");
        imageTab.setContent(imageBox);
        imageTab.setClosable(false);

        sourceEditor = new SceneTextEditor();
        sourceEditor.setWrapText(false);
        sourceEditor.setDisable(true);
        sourceTab = new Tab("Source (.scene)");
        VBox sourceBox = new VBox(sourceEditor);
        sourceBox.setPrefSize(Double.MAX_VALUE, Double.MAX_VALUE);
        VBox.setVgrow(sourceEditor, Priority.ALWAYS);
        sourceTab.setContent(sourceBox);
        sourceTab.setClosable(false);

        // Warnings tab
        warningsList = new ListView<>();
        warningsList.setPlaceholder(new javafx.scene.control.Label("Aucun warning"));
        warningsList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    // style warnings in orange
                    setStyle("-fx-text-fill: darkorange;");
                }
            }
        });
        warningsTab = new Tab("Warnings");
        VBox warnBox = new VBox(warningsList);
        warnBox.setPrefSize(Double.MAX_VALUE, Double.MAX_VALUE);
        VBox.setVgrow(warningsList, Priority.ALWAYS);
        warningsTab.setContent(warnBox);
        warningsTab.setClosable(false);

        tabPane.getTabs().addAll(imageTab, sourceTab, warningsTab);

        // Create parser/issues controller
        parserIssuesController = new ParserIssuesController(imageTab, sourceTab, warningsTab, warningsList,
                applyBtn, revertBtn, insertCameraBtn, saveSceneBtn, saveImageBtn, sourceEditor);

        // By default, no scene is loaded: disable all tabs and related UI
        try {
            imageTab.setDisable(true);
            sourceTab.setDisable(true);
            warningsTab.setDisable(true);
            warningsList.getItems().clear();
        } catch (Exception ignored) {}

        // When the user selects the Source tab for the first time, scroll the editor to the end
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab == sourceTab && !sourceTabViewed) {
                sourceTabViewed = true;
                try {
                    // SceneTextEditor provides scrollToStart
                    if (sourceEditor != null) sourceEditor.scrollToStart();
                } catch (Exception ignored) {}
            }
        });

        root.setTop(topBar);
        root.setCenter(tabPane);

        Scene fxScene = new Scene(root, width, height + 80);
        // Load editor highlighting stylesheet (if available)
        try {
            fxScene.getStylesheets().add(getClass().getResource("/editor-highlighting.css").toExternalForm());
        } catch (Exception ignored) {}

        // Capture key events at scene level but ignore when editor has focus
        fxScene.addEventFilter(KeyEvent.KEY_PRESSED, ev -> {
            // If the source editor has the focus, let it handle all keys (navigation, typing)
            try {
                if (sourceEditor != null && !sourceEditor.isDisable() && sourceEditor.isEditorFocused()) {
                    return;
                }
            } catch (Exception ignored) {}

            KeyCode code = ev.getCode();
            if (code == KeyCode.LEFT || code == KeyCode.RIGHT || code == KeyCode.UP || code == KeyCode.DOWN || code == KeyCode.A || code == KeyCode.R) {
                onKeyPressed(ev);
                ev.consume();
            }
        });

        stage.setTitle("RayTracer - Visualisation interactive (prototype)");
        stage.setScene(fxScene);
        stage.setOnCloseRequest(ev -> {
            try { if (renderService != null) renderService.shutdown(); } catch (Exception ignored) {}
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
            // successful parse
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
            imagePane.createCanvas(this.width, this.height);
            startRender(true);
            try {
                sourceEditor.setDisable(false);
                applyBtn.setDisable(false);
                revertBtn.setDisable(false);
                insertCameraBtn.setDisable(false);
                saveSceneBtn.setDisable(false);
                imageTab.setDisable(false);
                sourceTab.setDisable(false);
                warningsTab.setDisable(false);
                saveImageBtn.setDisable(false);
            } catch (Exception ignore) {}
            parserIssuesController.updateWarnings();
        } catch (Exception ex) {
            // If parse failed but is a ParserException, show it and still load the source file
            if (ParserIssuesController.isParserException(ex)) {
                parserIssuesController.handleParserException(ex, f);
            } else {
                ex.printStackTrace();
            }
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
                imagePane.createCanvas(this.width, this.height);
                currentScene = preview;
                imageTab.setDisable(false);
                sourceTab.setDisable(false);
                warningsTab.setDisable(false);
                saveImageBtn.setDisable(false);
                startRender(true);
            }
        } catch (Exception e) {
                if (ParserIssuesController.isParserException(e)) {
                // show parser error but allow user to continue editing
                Alert a = new Alert(Alert.AlertType.ERROR);
                a.setTitle("Erreur d'analyse");
                a.setHeaderText("ParserException lors de l'application des modifications");
                a.setContentText(e.getMessage());
                a.showAndWait();
                // disable image/tab since no image was generated
                try { imageTab.setDisable(true); saveImageBtn.setDisable(true); } catch (Exception ignored) {}
                parserIssuesController.updateWarnings();
            } else {
                e.printStackTrace();
            }
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
            imagePane.createCanvas(this.width, this.height);
            imageTab.setDisable(false);
            sourceTab.setDisable(false);
            warningsTab.setDisable(false);
            saveImageBtn.setDisable(false);
            startRender(true);
        } catch (Exception e) {
            if (ParserIssuesController.isParserException(e)) {
                parserIssuesController.handleParserException(e, originalSceneFile);
            } else {
                e.printStackTrace();
            }
        }
    }

    

    private void onKeyPressed(javafx.scene.input.KeyEvent ev) {
        if (currentScene == null) return;
        boolean changed = cameraController.handleKeyPressed(ev, currentScene);
        if (changed) startRender(true);
    }

    private synchronized void startRender(boolean lowRes) {
        RenderOptions opts = new RenderOptions();
        opts.samplesPerPixel = lowRes ? 1 : 10;
        opts.maxDepth = 5;
        opts.tileSize = 64;
        opts.threadCount = Runtime.getRuntime().availableProcessors();
        opts.lowResFactor = lowRes ? 0.4 : 1.0;
        opts.progressive = false;

        imagePane.createCanvas(width, height);

        renderService.render(currentScene, currentScene.getCamera(), width, height, opts,
                new ProgressListener() {
                    @Override
                    public void onUpdate(RenderUpdate update) {
                        Platform.runLater(() -> imagePane.applyBufferedPart(update.imagePart, update.x, update.y));
                    }
                }, img -> Platform.runLater(() -> imagePane.setImageFromBuffered(img)));
    }
    

}

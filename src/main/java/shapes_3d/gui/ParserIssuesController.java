package shapes_3d.gui;

import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;

import ray_tracer.parsing.SceneFileParser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * Helper that centralizes parser-related UI behaviour: display warnings
 * and handle ParserException by loading the source text into the editor
 * and enabling/disabling the relevant tabs and buttons.
 */
public class ParserIssuesController {

    private final Tab imageTab;
    private final Tab sourceTab;
    private final Tab warningsTab;
    private final ListView<String> warningsList;
    private final Button applyBtn;
    private final Button revertBtn;
    private final Button insertCameraBtn;
    private final Button saveSceneBtn;
    private final Button saveImageBtn;
    private final SceneTextEditor sourceEditor;

    public ParserIssuesController(Tab imageTab, Tab sourceTab, Tab warningsTab, ListView<String> warningsList,
                                  Button applyBtn, Button revertBtn, Button insertCameraBtn,
                                  Button saveSceneBtn, Button saveImageBtn, SceneTextEditor sourceEditor) {
        this.imageTab = imageTab;
        this.sourceTab = sourceTab;
        this.warningsTab = warningsTab;
        this.warningsList = warningsList;
        this.applyBtn = applyBtn;
        this.revertBtn = revertBtn;
        this.insertCameraBtn = insertCameraBtn;
        this.saveSceneBtn = saveSceneBtn;
        this.saveImageBtn = saveImageBtn;
        this.sourceEditor = sourceEditor;
    }

    public void updateWarnings() {
        try {
            List<String> warnings = SceneFileParser.getWarnings();
            if (warnings == null) warningsList.getItems().clear();
            else warningsList.getItems().setAll(warnings);
        } catch (Exception e) {
            warningsList.getItems().clear();
        }
    }

    public void handleParserException(Throwable ex, File sceneFile) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle("Erreur d'analyse");
        a.setHeaderText("ParserException lors du chargement");
        String msg = ex.getMessage();
        if (msg == null || msg.isEmpty()) msg = ex.toString();
        a.setContentText(msg);
        a.showAndWait();

        // Still load source text so user can edit
        try {
            try {
                String originalSceneContent = Files.readString(sceneFile.toPath(), StandardCharsets.UTF_8);
                sourceEditor.setText(originalSceneContent);
            } catch (IOException ioe) {
                sourceEditor.setText("");
            }
            // enable editor-related controls
            try { sourceEditor.setDisable(false); } catch (Exception ignored) {}
            try { applyBtn.setDisable(false); } catch (Exception ignored) {}
            try { revertBtn.setDisable(false); } catch (Exception ignored) {}
            try { insertCameraBtn.setDisable(false); } catch (Exception ignored) {}
            try { saveSceneBtn.setDisable(false); } catch (Exception ignored) {}

            // enable source & warnings tabs, block image access and image saving
            try { sourceTab.setDisable(false); warningsTab.setDisable(false); } catch (Exception ignored) {}
            try { imageTab.setDisable(true); saveImageBtn.setDisable(true); } catch (Exception ignored) {}

            updateWarnings();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean isParserException(Throwable t) {
        Throwable cur = t;
        while (cur != null) {
            try {
                if ("ParserException".equals(cur.getClass().getSimpleName())) return true;
            } catch (Exception ignored) {}
            cur = cur.getCause();
        }
        return false;
    }
}

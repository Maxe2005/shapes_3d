package shapes_3d.gui;

import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.fxmisc.flowless.VirtualizedScrollPane;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.util.Duration;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wrapper component that provides a virtualized text editor using RichTextFX.
 * Exposes a small, TextArea-like API used by the rest of the application.
 */
public class SceneTextEditor extends VBox {

    private final CodeArea codeArea;
    private final VirtualizedScrollPane<CodeArea> vsPane;

    public SceneTextEditor() {
        codeArea = new CodeArea();
        vsPane = new VirtualizedScrollPane<>(codeArea);
        this.getChildren().add(vsPane);
        VBox.setVgrow(vsPane, Priority.ALWAYS);
        // Keep inner CodeArea in sync with this control's disabled state
        disabledProperty().addListener((obs, oldVal, newVal) -> codeArea.setDisable(newVal));
        // Simple syntax highlighting debounce
        PauseTransition pause = new PauseTransition(Duration.millis(250));
        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            pause.stop();
            pause.setOnFinished(e -> applyHighlighting(newText));
            pause.playFromStart();
        });
    }

    public String getText() {
        return codeArea.getText();
    }

    public void setText(String text) {
        if (text == null) text = "";
        // replaceText keeps caret/undo behaviour consistent
        codeArea.replaceText(text);
    }

    /**
     * Scroll caret to the end and request follow caret in the UI thread.
     */
    public void scrollToEnd() {
        Platform.runLater(() -> {
            codeArea.moveTo(codeArea.getLength());
            codeArea.requestFollowCaret();
        });
    }

    /**
     * Scroll caret to the start (beginning of document) and request follow caret in the UI thread.
     */
    public void scrollToStart() {
        Platform.runLater(() -> {
            codeArea.moveTo(0);
            codeArea.requestFollowCaret();
        });
    }

    /**
     * Add a text change listener (convenience wrapper).
     */
    public void addTextChangeListener(ChangeListener<String> listener) {
        codeArea.textProperty().addListener(listener);
    }

    // --- simple syntax highlighting implementation -----------------
    private static final String[] KEYWORDS = new String[] {
            "camera", "size", "sphere", "material", "light", "plane", "box", "triangle",
            "translate", "rotate", "scale", "background", "output"
    };

    private static final String KEYWORD_PATTERN = "\\b(" + String.join("|", KEYWORDS) + ")\\b";
    private static final String COMMENT_PATTERN = "(#.*$|//.*$)";
    private static final String NUMBER_PATTERN = "\\b\\d+(?:\\.\\d+)?\\b";

    private static final Pattern PATTERN = Pattern.compile(
            "(?m)" +
                    "(?<KEYWORD>" + KEYWORD_PATTERN + ")" +
                    "|(?<COMMENT>" + COMMENT_PATTERN + ")" +
                    "|(?<NUMBER>" + NUMBER_PATTERN + ")"
    );

    private void applyHighlighting(String text) {
        if (text == null || text.isEmpty()) {
            StyleSpansBuilder<Collection<String>> emptyBuilder = new StyleSpansBuilder<>();
            emptyBuilder.add(Collections.emptyList(), 0);
            codeArea.setStyleSpans(0, emptyBuilder.create());
            return;
        }
        Matcher matcher = PATTERN.matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        while (matcher.find()) {
            String styleClass =
                    matcher.group("KEYWORD") != null ? "keyword" :
                    matcher.group("COMMENT") != null ? "comment" :
                    matcher.group("NUMBER") != null ? "number" :
                    null;
            assert styleClass != null;
            spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
            spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
            lastKwEnd = matcher.end();
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
        StyleSpans<Collection<String>> spans = spansBuilder.create();
        Platform.runLater(() -> codeArea.setStyleSpans(0, spans));
    }

    // Propagate disabled property to inner CodeArea so callers can use the regular Node API
    

    // Compatibility helper - no-op for CodeArea
    public void setWrapText(boolean wrap) {
        // CodeArea handles wrapping differently; keep as no-op for compatibility
    }

    @Override
    public void requestFocus() {
        codeArea.requestFocus();
    }
}

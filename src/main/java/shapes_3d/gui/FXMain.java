package shapes_3d.gui;

import javafx.application.Application;
import javafx.stage.Stage;

/**
 * Minimal Application class that delegates UI construction and logic
 * to GuiController to keep this class light and focused.
 */
public class FXMain extends Application {

    @Override
    public void start(Stage stage) {
        new GuiController().init(stage);
    }

    public static void main(String[] args) {
        launch(args);
    }

}

package com.kilocade.bubbleshooter;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Entry point for Comet Bloom.
 */
public final class Main extends Application {

    private static final String TITLE = "Comet Bloom";
    private static final double MIN_STAGE_WIDTH = 760;
    private static final double MIN_STAGE_HEIGHT = 760;

    private GameController controller;

    @Override
    public void start(Stage stage) {
        GameController newController = new GameController();
        try {
            Scene scene = newController.createScene();

            stage.setTitle(TITLE);
            stage.setScene(scene);
            stage.setMinWidth(MIN_STAGE_WIDTH);
            stage.setMinHeight(MIN_STAGE_HEIGHT);
            stage.setResizable(true);
            stage.show();

            controller = newController;
            Platform.runLater(newController::refreshLayout);
            newController.start();
        } catch (RuntimeException exception) {
            newController.stop();
            Platform.exit();
            throw exception;
        }
    }

    @Override
    public void stop() {
        GameController activeController = controller;
        controller = null;

        if (activeController != null) {
            activeController.stop();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}

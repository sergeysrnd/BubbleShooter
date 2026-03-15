package com.kilocade.bubbleshooter;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Entry point for Comet Bloom.
 */
public class Main extends Application {

    private GameController controller;

    @Override
    public void start(Stage stage) {
        controller = new GameController();
        Scene scene = controller.createScene();

        stage.setTitle("Comet Bloom");
        stage.setScene(scene);
        stage.setMinWidth(760);
        stage.setMinHeight(760);
        stage.setResizable(true);
        stage.show();
        Platform.runLater(controller::refreshLayout);

        controller.start();
    }

    @Override
    public void stop() {
        if (controller != null) {
            controller.stop();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}

package com.kilocade.bubbleshooter;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Entry point for the Bubble Shooter prototype.
 * Sets up the primary window and delegates lifecycle management to {@link GameController}.
 */
public class Main extends Application {

    private GameController controller;

    @Override
    public void start(Stage stage) {
        controller = new GameController();
        Scene scene = controller.createScene();

        stage.setTitle("Bubble Shooter Prototype");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();

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
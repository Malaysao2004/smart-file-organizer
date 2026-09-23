package com.smartorganizer;

import com.smartorganizer.controller.MainController;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.Objects;

public class Main extends Application {

    @Override
    public void start(Stage stage) {
        MainController controller = new MainController(stage);
        Scene scene = new Scene(controller.getRoot(), 1200, 760);
        scene.getStylesheets().add(
                Objects.requireNonNull(Main.class.getResource("/css/style.css")).toExternalForm());
        stage.setTitle("Smart File Organizer & Duplicate Finder");
        stage.setMinWidth(1000);
        stage.setMinHeight(650);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
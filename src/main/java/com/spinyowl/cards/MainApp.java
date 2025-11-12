package com.spinyowl.cards;

import com.spinyowl.cards.controller.PreviewController;
import com.spinyowl.cards.service.ProjectManager;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.nio.file.Path;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/preview.fxml"));
        Scene scene = new Scene(loader.load(), 1000, 700);

        PreviewController controller = loader.getController();
        // Open default example project on startup
        Path projectDir = Path.of("src/main/resources");
        ProjectManager pm = new ProjectManager();
        pm.openProject(projectDir);
        controller.setProject(pm);

        stage.setTitle("Card Renderer Preview");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

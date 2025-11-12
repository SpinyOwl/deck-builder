package com.spinyowl.cards.controller;

import com.spinyowl.cards.service.ProjectCreator;
import com.spinyowl.cards.service.ProjectManager;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.file.Path;

@Slf4j
public class StartupController {

    @FXML private Label statusLabel;

    @FXML
    public void onCreateProject() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Project Folder");
        File dir = chooser.showDialog(null);
        if (dir == null) return;

        try {
            String name = "NewCardProject";
            Path projectDir = dir.toPath().resolve(name);
            ProjectCreator.createDefaultProject(projectDir, name);
            openProject(projectDir);
        } catch (Exception e) {
            log.error("Failed to create project", e);
            statusLabel.setText("Error creating project: " + e.getMessage());
        }
    }

    @FXML
    public void onOpenProject() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Open Project Folder");
        File dir = chooser.showDialog(null);
        if (dir == null) return;
        openProject(dir.toPath());
    }

    private void openProject(Path dir) {
        try {
            ProjectManager pm = new ProjectManager();
            pm.openProject(dir);

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/main/ui/preview.fxml"));
            Scene scene = new Scene(loader.load(), 1000, 700);
            PreviewController controller = loader.getController();
            controller.setProject(pm);

            Stage stage = (Stage) statusLabel.getScene().getWindow();
            stage.setScene(scene);
            stage.setTitle("Card Renderer - " + pm.getProjectName());
            stage.show();
        } catch (Exception e) {
            log.error("Failed to open project", e);
            statusLabel.setText("Failed to open project: " + e.getMessage());
        }
    }
}

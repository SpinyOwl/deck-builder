package com.spinyowl.cards.controller;

import com.spinyowl.cards.config.ConfigService;
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

    private final ConfigService configService = ConfigService.getInstance();

    @FXML
    public void onCreateProject() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Project Folder");
        File parent = configService.getLastProjectsParentDirectory();
        if (parent != null) {
            chooser.setInitialDirectory(parent);
        }
        File dir = chooser.showDialog(statusLabel.getScene().getWindow());
        if (dir == null) return;

        try {
            Path projectDir = dir.toPath();
            Path fileName = projectDir.getFileName();
            String name = fileName != null && !fileName.toString().isEmpty() ? fileName.toString() : "NewCardProject";
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
        File recent = configService.getMostRecentProjectDirectory();
        if (recent != null) {
            chooser.setInitialDirectory(recent);
        } else {
            File parent = configService.getLastProjectsParentDirectory();
            if (parent != null) {
                chooser.setInitialDirectory(parent);
            }
        }
        File dir = chooser.showDialog(statusLabel.getScene().getWindow());
        if (dir == null) return;
        openProject(dir.toPath());
    }

    private void openProject(Path dir) {
        try {
            ProjectManager pm = new ProjectManager();
            pm.openProject(dir);
            configService.markProjectOpened(dir);
            configService.setLastProjectsParent(dir.getParent());

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/main/ui/preview.fxml"));
            Stage stage = (Stage) statusLabel.getScene().getWindow();
            double width = stage.getWidth();
            double height = stage.getHeight();
            Scene scene = new Scene(loader.load());
            PreviewController controller = loader.getController();
            controller.setProject(pm);

            stage.setScene(scene);
            if (width > 0 && height > 0) {
                stage.setWidth(width);
                stage.setHeight(height);
            }
            stage.setTitle("Card Renderer - " + pm.getProjectName());
            stage.show();
        } catch (Exception e) {
            log.error("Failed to open project", e);
            statusLabel.setText("Failed to open project: " + e.getMessage());
        }
    }
}

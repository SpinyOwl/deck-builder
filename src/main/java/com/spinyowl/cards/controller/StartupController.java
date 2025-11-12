package com.spinyowl.cards.controller;

import com.spinyowl.cards.config.ConfigService;
import com.spinyowl.cards.service.ProjectCreator;
import com.spinyowl.cards.service.ProjectManager;
import com.spinyowl.cards.ui.WindowStateHandler;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

import static com.spinyowl.cards.config.AppPaths.APPLICATION_ID;

@Slf4j
public class StartupController {

    @FXML
    private Label statusLabel;
    @FXML
    private ListView<String> recentProjectsList;

    private final ConfigService configService = ConfigService.getInstance();
    private final ObservableList<String> recentProjectsItems = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        if (recentProjectsList != null) {
            recentProjectsList.setItems(recentProjectsItems);
            recentProjectsList.setCellFactory(list -> new RecentProjectCell());
            refreshRecentProjects();
        }
    }

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

    private void refreshRecentProjects() {
        recentProjectsItems.setAll(configService.getConfig().getRecentProjects());
    }

    private void openProject(Path dir) {
        try {
            ProjectManager pm = new ProjectManager();
            pm.openProject(dir);
            configService.markProjectOpened(dir);
            configService.setLastProjectsParent(dir.getParent());

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/main/ui/preview.fxml"));
            Stage stage = (Stage) statusLabel.getScene().getWindow();
            Scene scene = new Scene(loader.load());
            PreviewController controller = loader.getController();
            controller.setProject(pm);

            stage.setScene(scene);
            stage.setResizable(true);
            WindowStateHandler.enable(stage, configService);
            stage.setTitle(APPLICATION_ID + " - " + pm.getProjectName());
            stage.show();
        } catch (Exception e) {
            log.error("Failed to open project", e);
            statusLabel.setText("Failed to open project: " + e.getMessage());
        }
    }

    private class RecentProjectCell extends ListCell<String> {
        private final Label nameLabel = new Label();
        private final Button deleteButton = new Button("✕");
        private final Region spacer = new Region();
        private final HBox container = new HBox(10, nameLabel, spacer, deleteButton);

        private RecentProjectCell() {
            HBox.setHgrow(spacer, Priority.ALWAYS);
            deleteButton.setFocusTraversable(false);
            deleteButton.getStyleClass().add("recent-project-delete-button");
            deleteButton.setOnAction(this::onClickOnDelete);
            container.setOnMouseClicked(this::onMouseClickOnItem);
            container.setAlignment(Pos.CENTER_LEFT);
        }

        private void onClickOnDelete(ActionEvent event) {
            String item = getItem();
            if (item != null) {
                configService.removeRecentProject(item);
                refreshRecentProjects();
            }
            event.consume();
        }

        private void onMouseClickOnItem(MouseEvent event) {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 1) {
                Node target = event.getPickResult() != null ? event.getPickResult().getIntersectedNode() : null;
                while (target != null) {
                    if (target == deleteButton) {
                        return;
                    }
                    target = target.getParent();
                }
                String item = getItem();
                if (item != null) {
                    openRecentProject(item);
                }
            }
        }

        private void openRecentProject(String projectPath) {
            try {
                Path path = Path.of(projectPath);
                if (!Files.isDirectory(path)) {
                    statusLabel.setText("Project directory not found: " + projectPath);
                    configService.removeRecentProject(projectPath);
                    refreshRecentProjects();
                    return;
                }
                openProject(path);
            } catch (InvalidPathException e) {
                statusLabel.setText("Invalid project path: " + projectPath);
                configService.removeRecentProject(projectPath);
                refreshRecentProjects();
            }
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                nameLabel.setText(item);
                setText(null);
                setGraphic(container);
            }
        }
    }
}

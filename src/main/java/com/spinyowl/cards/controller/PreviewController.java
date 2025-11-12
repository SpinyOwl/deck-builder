package com.spinyowl.cards.controller;

import com.spinyowl.cards.service.CardRenderer;
import com.spinyowl.cards.service.ProjectManager;
import com.spinyowl.cards.service.ProjectWatcher;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;

import java.awt.Desktop;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class PreviewController {

    @FXML private WebView webView;
    @FXML private TextField indexField;
    @FXML private ComboBox<String> langBox;
    @FXML private TreeView<Path> projectTree;
    @FXML private TitledPane consolePane;
    @FXML private TextArea consoleTextArea;

    private ProjectManager projectManager;
    private CardRenderer renderer;
    private ProjectWatcher projectWatcher;
    private final AtomicBoolean reloadScheduled = new AtomicBoolean();

    public void setProject(ProjectManager pm) {
        this.projectManager = pm;
        this.renderer = new CardRenderer(pm);

        langBox.getItems().addAll("en");
        langBox.getSelectionModel().selectFirst();
        indexField.setText("0");
        initProjectTree();
        refresh();
        if (projectManager != null && projectManager.getProjectDir() != null) {
            appendLog("Loaded project: " + projectManager.getProjectDir());
        }
        startWatcher();
    }

    @FXML
    public void onReload() {
        performReload();
        appendLog("Templates reloaded.");
    }

    @FXML
    public void onShow() {
        refresh();
    }

    @FXML
    public void onCloseProject() {
        stopWatcher();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/main/ui/startup.fxml"));
            Scene scene = new Scene(loader.load(), 800, 600);
            Stage stage = (Stage) webView.getScene().getWindow();
            stage.setScene(scene);
            stage.setTitle("SpinyOwl.DeckBuilder");
            stage.show();
            appendLog("Project closed.");
        } catch (IOException e) {
            appendError("Failed to close project view", e);
        }
    }

    private void refresh() {
        try {
            int idx = Integer.parseInt(indexField.getText().trim());
            String html = renderer.renderCard(idx, langBox.getValue());
            Platform.runLater(() -> webView.getEngine().loadContent(html));
            appendLog(String.format("Rendered card %d (%s)", idx, langBox.getValue()));
        } catch (Exception e) {
            appendError("Error rendering card", e);
            webView.getEngine().loadContent("<p>Error rendering card.</p>");
        }
    }

    private void performReload() {
        if (projectManager == null || renderer == null) {
            return;
        }

        try {
            projectManager.reloadProject();
        } catch (IOException e) {
            log.error("Failed to reload project configuration", e);
        }

        renderer.reload();
        initProjectTree();
        refresh();
    }

    private void initProjectTree() {
        if (projectManager == null || projectManager.getProjectDir() == null) {
            return;
        }

        Path rootPath = projectManager.getProjectDir();
        TreeItem<Path> rootItem = createTreeItem(rootPath);
        rootItem.setExpanded(true);
        projectTree.setRoot(rootItem);
        projectTree.setShowRoot(true);
        projectTree.setCellFactory(treeView -> createPathTreeCell());
    }

    private TreeItem<Path> createTreeItem(Path path) {
        TreeItem<Path> item = new TreeItem<>(path);
        if (Files.isDirectory(path)) {
            try {
                List<Path> children;
                try (var stream = Files.list(path)) {
                    children = stream
                            .sorted(directoryFirst())
                            .toList();
                }
                for (Path child : children) {
                    item.getChildren().add(createTreeItem(child));
                }
            } catch (IOException e) {
                appendError("Failed to read directory: " + path, e);
            }
        }
        return item;
    }

    private void startWatcher() {
        stopWatcher();
        if (projectManager == null || projectManager.getProjectDir() == null) {
            return;
        }

        try {
            projectWatcher = new ProjectWatcher(projectManager.getProjectDir(), path -> scheduleReload());
        } catch (IOException e) {
            log.warn("Unable to start project watcher", e);
        }
    }

    private void stopWatcher() {
        if (projectWatcher != null) {
            try {
                projectWatcher.close();
            } catch (IOException e) {
                log.warn("Failed to stop project watcher", e);
            }
            projectWatcher = null;
        }
    }

    private void scheduleReload() {
        if (!reloadScheduled.compareAndSet(false, true)) {
            return;
        }

        Platform.runLater(() -> {
            try {
                performReload();
            } finally {
                reloadScheduled.set(false);
            }
        });
    }

    private Comparator<Path> directoryFirst() {
        return Comparator
                .comparing((Path p) -> !Files.isDirectory(p))
                .thenComparing(Path::getFileName, Comparator.nullsFirst(Comparator.naturalOrder()));
    }

    private TreeCell<Path> createPathTreeCell() {
        TreeCell<Path> cell = new TreeCell<>() {
            @Override
            protected void updateItem(Path item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Path fileName = item.getFileName();
                    setText(fileName != null ? fileName.toString() : item.toString());
                }
            }
        };

        cell.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && !cell.isEmpty()) {
                Path path = cell.getItem();
                if (Files.isDirectory(path)) {
                    TreeItem<Path> treeItem = cell.getTreeItem();
                    treeItem.setExpanded(!treeItem.isExpanded());
                } else {
                    openInDesktop(path);
                }
            }
        });

        return cell;
    }

    private void openInDesktop(Path path) {
        if (!Desktop.isDesktopSupported()) {
            return;
        }

        Desktop desktop = Desktop.getDesktop();
        if (!desktop.isSupported(Desktop.Action.OPEN)) {
            return;
        }

        try {
            desktop.open(path.toFile());
        } catch (IOException e) {
            appendError("Failed to open file: " + path, e);
        }
    }

    private void appendLog(String message) {
        if (consoleTextArea == null) {
            return;
        }
        Platform.runLater(() -> {
            if (!consoleTextArea.getText().isEmpty()) {
                consoleTextArea.appendText(System.lineSeparator());
            }
            consoleTextArea.appendText(message);
            consoleTextArea.positionCaret(consoleTextArea.getText().length());
        });
    }

    private void appendError(String message, Exception e) {
        if (consoleTextArea == null) {
            if (e != null) {
                e.printStackTrace();
            }
            return;
        }
        StringWriter sw = new StringWriter();
        if (e != null) {
            e.printStackTrace(new PrintWriter(sw));
        }
        String stackTrace = sw.toString();
        Platform.runLater(() -> {
            if (consolePane != null) {
                consolePane.setExpanded(true);
            }
            if (!consoleTextArea.getText().isEmpty()) {
                consoleTextArea.appendText(System.lineSeparator());
            }
            consoleTextArea.appendText(message);
            if (!stackTrace.isEmpty()) {
                consoleTextArea.appendText(System.lineSeparator());
                consoleTextArea.appendText(stackTrace);
            }
            consoleTextArea.positionCaret(consoleTextArea.getText().length());
        });
    }
}

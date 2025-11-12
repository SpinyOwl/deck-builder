package com.spinyowl.cards.controller;

import com.spinyowl.cards.config.AppPaths;
import com.spinyowl.cards.service.CardRenderer;
import com.spinyowl.cards.service.ProjectManager;
import com.spinyowl.cards.service.ProjectWatcher;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;

import javafx.scene.Node;
import javafx.scene.layout.StackPane;

import java.awt.Desktop;
import java.nio.file.FileAlreadyExistsException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.nio.charset.StandardCharsets;

@Slf4j
public class PreviewController {

    private static final int MAX_LOG_CHARACTERS = 20_000;

    @FXML private WebView webView;
    @FXML private TextField indexField;
    @FXML private ComboBox<String> langBox;
    @FXML private TreeView<Path> projectTree;
    @FXML private TitledPane consolePane;
    @FXML private TextArea consoleTextArea;
    @FXML private SplitPane mainVerticalSplit;
    @FXML private SplitPane mainHorizontalSplit;
    @FXML private StackPane projectTreeContainer;
    @FXML private ToggleButton projectTreeToggle;

    private ProjectManager projectManager;
    private CardRenderer renderer;
    private ProjectWatcher projectWatcher;
    private final AtomicBoolean reloadScheduled = new AtomicBoolean();
    private ScheduledExecutorService logUpdateExecutor;
    private Path latestLogFile;
    private volatile long lastLogModified = -1L;
    private volatile long lastLogSize = -1L;
    private volatile String lastDisplayedLog;
    private volatile double storedDividerPos = 0.8; // remember previous divider position when collapsing console
    private volatile boolean dividerStored = false;
    private volatile double storedProjectTreeDividerPos = 0.25;
    private volatile boolean projectDividerStored = false;

    @FXML
    public void initialize() {
        // Hook up collapse/expand behavior so the top pane regains space when console is collapsed
        if (consolePane != null) {
            consolePane.expandedProperty().addListener((obs, wasExpanded, isNowExpanded) -> {
                if (mainVerticalSplit == null) {
                    return;
                }
                if (isNowExpanded) {
                    // restore resizable and divider position
                    SplitPane.setResizableWithParent(consolePane, true);
                    double target = dividerStored ? storedDividerPos : 0.8;
                    Platform.runLater(() -> {
                        mainVerticalSplit.setDividerPositions(clamp(target));
                        updateVerticalDividerDraggable(true);
                    });
                } else {
                    // store current divider position and collapse console area visually
                    if (!mainVerticalSplit.getDividers().isEmpty()) {
                        storedDividerPos = mainVerticalSplit.getDividers().get(0).getPosition();
                        dividerStored = true;
                    }
                    SplitPane.setResizableWithParent(consolePane, false);
                    Platform.runLater(() -> {
                        mainVerticalSplit.setDividerPositions(1.0);
                        updateVerticalDividerDraggable(false);
                    });
                }
            });

            // Apply initial state: if it's collapsed at startup, ensure divider is at 1.0 and console doesn't take space
            if (mainVerticalSplit != null && !consolePane.isExpanded()) {
                SplitPane.setResizableWithParent(consolePane, false);
                Platform.runLater(() -> {
                    mainVerticalSplit.setDividerPositions(1.0);
                    updateVerticalDividerDraggable(false);
                });
            }
        }
        if (projectTreeToggle != null) {
            updateProjectTreeVisibility(projectTreeToggle.isSelected());
        }
    }

    private double clamp(double pos) {
        if (pos < 0.0) return 0.0;
        if (pos > 1.0) return 1.0;
        return pos;
    }

    @FXML
    private void onToggleProjectTree() {
        if (projectTreeToggle == null) {
            return;
        }
        updateProjectTreeVisibility(projectTreeToggle.isSelected());
    }

    private void updateProjectTreeVisibility(boolean show) {
        if (mainHorizontalSplit == null || projectTreeContainer == null) {
            return;
        }

        ObservableList<Node> items = mainHorizontalSplit.getItems();
        boolean currentlyVisible = items.contains(projectTreeContainer);

        if (show && !currentlyVisible) {
            items.add(0, projectTreeContainer);
            SplitPane.setResizableWithParent(projectTreeContainer, true);
            double target = projectDividerStored ? storedProjectTreeDividerPos : 0.25;
            Platform.runLater(() -> mainHorizontalSplit.setDividerPositions(clamp(target)));
        } else if (!show && currentlyVisible) {
            if (!mainHorizontalSplit.getDividers().isEmpty()) {
                storedProjectTreeDividerPos = mainHorizontalSplit.getDividers().get(0).getPosition();
                projectDividerStored = true;
            }
            SplitPane.setResizableWithParent(projectTreeContainer, false);
            items.remove(projectTreeContainer);
        }
    }

    /**
     * Enables or disables user dragging on the divider of the vertical SplitPane that hosts the console.
     * When the console is collapsed, the divider should not be draggable to avoid accidental resizing.
     */
    private void updateVerticalDividerDraggable(boolean draggable) {
        if (mainVerticalSplit == null) return;
        // run later to ensure the skin and divider nodes are present
        Platform.runLater(() -> {
            for (Node n : mainVerticalSplit.lookupAll(".split-pane-divider")) {
                // Disable mouse interaction on the divider when not draggable
                n.setMouseTransparent(!draggable);
            }
        });
    }

    public void setProject(ProjectManager pm) {
        this.projectManager = pm;
        this.renderer = new CardRenderer(pm);

        langBox.getItems().addAll("en");
        langBox.getSelectionModel().selectFirst();
        indexField.setText("0");
        initProjectTree();
        refresh();
        startWatcher();
        startLogUpdates();
        if (projectManager != null && projectManager.getProjectDir() != null) {
            log.info("Preview initialized for {}", projectManager.getProjectDir());
        }
    }

    @FXML
    public void onReload() {
        performReload();
        log.info("Manual template reload requested");
    }

    @FXML
    public void onShow() {
        refresh();
    }

    @FXML
    public void onCloseProject() {
        stopWatcher();
        stopLogUpdates();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/main/ui/startup.fxml"));
            Scene scene = new Scene(loader.load(), 800, 600);
            Stage stage = (Stage) webView.getScene().getWindow();
            stage.setScene(scene);
            stage.setTitle("SpinyOwl.DeckBuilder");
            stage.show();
            log.info("Project closed");
        } catch (IOException e) {
            log.error("Failed to close project view", e);
            expandConsole();
        }
    }

    private void refresh() {
        try {
            int idx = Integer.parseInt(indexField.getText().trim());
            String html = renderer.renderCard(idx, langBox.getValue());
            Platform.runLater(() -> webView.getEngine().loadContent(html));
            log.info("Rendered card {} ({})", idx, langBox.getValue());
        } catch (Exception e) {
            log.error("Error rendering card", e);
            expandConsole();
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
                e.printStackTrace();
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

        ContextMenu contextMenu = new ContextMenu();
        MenuItem newFileItem = new MenuItem("New File…");
        MenuItem newFolderItem = new MenuItem("New Folder…");
        contextMenu.getItems().addAll(newFileItem, newFolderItem);

        newFileItem.setOnAction(event -> handleCreateEntry(cell, false));
        newFolderItem.setOnAction(event -> handleCreateEntry(cell, true));

        cell.emptyProperty().addListener((obs, wasEmpty, isEmpty) -> {
            if (isEmpty) {
                cell.setContextMenu(null);
            } else {
                cell.setContextMenu(contextMenu);
            }
        });

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

    private void handleCreateEntry(TreeCell<Path> cell, boolean directory) {
        Path selected = cell.getItem();
        if (selected == null) {
            return;
        }

        TreeItem<Path> treeItem = cell.getTreeItem();
        TreeItem<Path> targetItem = Files.isDirectory(selected) ? treeItem : treeItem.getParent();
        Path targetDir = Files.isDirectory(selected) ? selected : selected.getParent();

        if (targetItem == null) {
            targetItem = treeItem;
        }

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle(directory ? "Create Folder" : "Create File");
        dialog.setHeaderText(null);
        dialog.setContentText("Enter " + (directory ? "folder" : "file") + " name:");
        if (projectTree.getScene() != null) {
            dialog.initOwner(projectTree.getScene().getWindow());
        }

        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty()) {
            return;
        }

        String name = result.get().trim();
        if (name.isEmpty()) {
            return;
        }

        if (name.contains("/") || name.contains("\\") || name.contains(":")) {
            showErrorAlert("Invalid Name", "Name must not contain path separators or colon characters.");
            return;
        }

        if (targetDir == null) {
            showErrorAlert("Invalid Selection", "Unable to determine target directory for new entry.");
            return;
        }

        Path newPath = targetDir.resolve(name);

        try {
            if (directory) {
                Files.createDirectories(newPath);
            } else {
                Files.createFile(newPath);
            }
            addChildToTree(targetItem, newPath);
        } catch (FileAlreadyExistsException e) {
            showErrorAlert("Already Exists", "An entry named '" + name + "' already exists.");
        } catch (IOException e) {
            showErrorAlert("Creation Failed", "Unable to create " + (directory ? "folder" : "file") + ".\n" + e.getMessage());
        }
    }

    private void addChildToTree(TreeItem<Path> parent, Path childPath) {
        if (parent == null) {
            initProjectTree();
            return;
        }

        TreeItem<Path> newChild = createTreeItem(childPath);
        var children = parent.getChildren();
        Comparator<TreeItem<Path>> comparator = Comparator.comparing(TreeItem<Path>::getValue, directoryFirst());

        int index = 0;
        while (index < children.size() && comparator.compare(newChild, children.get(index)) > 0) {
            index++;
        }
        children.add(index, newChild);
        parent.setExpanded(true);
    }

    private void showErrorAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        if (projectTree.getScene() != null) {
            alert.initOwner(projectTree.getScene().getWindow());
        }
        alert.showAndWait();
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
            log.error("Failed to open file {}", path, e);
            expandConsole();
        }
    }

    private void startLogUpdates() {
        stopLogUpdates();
        latestLogFile = AppPaths.getLatestLogFile();
        lastLogModified = -1L;
        lastLogSize = -1L;
        lastDisplayedLog = null;
        logUpdateExecutor = Executors.newSingleThreadScheduledExecutor(new LogWatcherThreadFactory());
        logUpdateExecutor.scheduleWithFixedDelay(this::refreshConsoleFromLog, 0, 1, TimeUnit.SECONDS);
    }

    private void stopLogUpdates() {
        if (logUpdateExecutor != null) {
            logUpdateExecutor.shutdownNow();
            logUpdateExecutor = null;
        }
        latestLogFile = null;
        lastDisplayedLog = null;
        lastLogModified = -1L;
        lastLogSize = -1L;
    }

    private void refreshConsoleFromLog() {
        if (consoleTextArea == null) {
            return;
        }

        Path logFile = latestLogFile;
        if (logFile == null || !Files.exists(logFile)) {
            if (lastDisplayedLog != null && !lastDisplayedLog.isEmpty()) {
                lastDisplayedLog = "";
                Platform.runLater(() -> {
                    consoleTextArea.clear();
                    consoleTextArea.positionCaret(0);
                });
            }
            lastLogModified = -1L;
            lastLogSize = -1L;
            return;
        }

        try {
            long modified = Files.getLastModifiedTime(logFile).toMillis();
            long size = Files.size(logFile);
            if (modified == lastLogModified && size == lastLogSize) {
                return;
            }
            lastLogModified = modified;
            lastLogSize = size;

            String content = Files.readString(logFile, StandardCharsets.UTF_8);
            if (content.length() > MAX_LOG_CHARACTERS) {
                content = content.substring(content.length() - MAX_LOG_CHARACTERS);
            }
            if (content.equals(lastDisplayedLog)) {
                return;
            }
            lastDisplayedLog = content;
            String finalContent = content;
            Platform.runLater(() -> {
                consoleTextArea.setText(finalContent);
                consoleTextArea.positionCaret(finalContent.length());
            });
        } catch (IOException e) {
            log.warn("Failed to read log file {}", logFile, e);
        }
    }

    private void expandConsole() {
        if (consolePane != null) {
            Platform.runLater(() -> consolePane.setExpanded(true));
        }
    }

    private static class LogWatcherThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, "log-viewer");
            thread.setDaemon(true);
            return thread;
        }
    }
}

package com.spinyowl.cards.controller;

import com.spinyowl.cards.service.CardRenderer;
import com.spinyowl.cards.service.ProjectManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

import java.io.IOException;

public class PreviewController {

    @FXML private WebView webView;
    @FXML private TextField indexField;
    @FXML private ComboBox<String> langBox;

    private ProjectManager projectManager;
    private CardRenderer renderer;

    public void setProject(ProjectManager pm) {
        this.projectManager = pm;
        this.renderer = new CardRenderer(pm);

        langBox.getItems().addAll("en");
        langBox.getSelectionModel().selectFirst();
        indexField.setText("0");
        refresh();
    }

    @FXML
    public void onReload() {
        renderer.reload();
        refresh();
    }

    @FXML
    public void onShow() {
        refresh();
    }

    @FXML
    public void onCloseProject() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/main/ui/startup.fxml"));
            Scene scene = new Scene(loader.load(), 800, 600);
            Stage stage = (Stage) webView.getScene().getWindow();
            stage.setScene(scene);
            stage.setTitle("SpinyOwl.DeckBuilder");
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void refresh() {
        try {
            int idx = Integer.parseInt(indexField.getText().trim());
            String html = renderer.renderCard(idx, langBox.getValue());
            Platform.runLater(() -> webView.getEngine().loadContent(html));
        } catch (Exception e) {
            e.printStackTrace();
            webView.getEngine().loadContent("<p>Error rendering card.</p>");
        }
    }
}

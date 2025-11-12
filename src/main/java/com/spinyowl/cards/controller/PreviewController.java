package com.spinyowl.cards.controller;

import com.spinyowl.cards.service.CardRenderer;
import com.spinyowl.cards.service.ProjectManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.web.WebView;

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

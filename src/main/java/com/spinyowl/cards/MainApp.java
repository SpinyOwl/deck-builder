package com.spinyowl.cards;

import com.spinyowl.cards.config.ConfigService;
import com.spinyowl.cards.logging.LoggingInitializer;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {

    private final ConfigService configService = ConfigService.getInstance();

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/main/ui/startup.fxml"));
        Scene scene = new Scene(loader.load(), 400, 400);

        stage.setTitle("SpinyOwl.DeckBuilder");
        stage.setScene(scene);
        stage.setResizable(false);

        stage.setOnCloseRequest(event -> configService.save());

        stage.show();
    }

    public static void main(String[] args) {
        LoggingInitializer.initialize();
        launch(args);
    }
}

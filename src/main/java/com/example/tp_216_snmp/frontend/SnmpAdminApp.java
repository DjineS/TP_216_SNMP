package com.example.tp_216_snmp.frontend;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.io.IOException;
import java.net.URL;

public class SnmpAdminApp extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        // Charger l'interface de bienvenue
        URL fxmlLocation = getClass().getResource("/com/example/tp_216_snmp/frontend/welcome.fxml");
        if (fxmlLocation == null) {
            throw new IOException("Cannot find FXML file at /com/example/tp_216_snmp/frontend/welcome.fxml");
        }
        FXMLLoader fxmlLoader = new FXMLLoader(fxmlLocation);
        Scene scene = new Scene(fxmlLoader.load(), 1200, 900);
        stage.setTitle("TP ICT216 - ADMINISTRATION RESEAU");
        stage.setScene(scene);
        stage.setResizable(true);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}
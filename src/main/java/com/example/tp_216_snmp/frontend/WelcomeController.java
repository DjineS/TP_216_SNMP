package com.example.tp_216_snmp.frontend;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

public class WelcomeController {

    @FXML
    private void selectAgent(ActionEvent event) throws IOException {
        loadInterface(event, "Agent", "/com/example/tp_216_snmp/frontend/agent.fxml");
    }

    @FXML
    private void selectAdmin(ActionEvent event) throws IOException {
        loadInterface(event, "Admin", "/com/example/tp_216_snmp/frontend/admin.fxml");
    }

    private void loadInterface(ActionEvent event, String role, String fxmlPath) throws IOException {
        URL fxmlLocation = getClass().getResource(fxmlPath);
        if (fxmlLocation == null) {
            throw new IOException("Cannot find FXML file at " + fxmlPath);
        }
        FXMLLoader fxmlLoader = new FXMLLoader(fxmlLocation);
        Scene scene = new Scene(fxmlLoader.load(), 1200, 900);
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.setScene(scene);
        stage.setTitle("Administrateur Réseau SNMP - " + role);
        stage.setResizable(true);
        stage.show();
    }
}
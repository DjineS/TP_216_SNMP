package com.example.tp_216_snmp.frontend;

import com.example.tp_216_snmp.backend.model.Device;
import com.example.tp_216_snmp.backend.network.NetworkScanCallback;
import com.example.tp_216_snmp.backend.network.NetworkScanner;
import com.example.tp_216_snmp.backend.snmp.SnmpClient;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.List;

public class SnmpController {
    @FXML private ListView<Device> deviceList;
    @FXML private CheckBox showNamesCheckBox;
    @FXML private Label statusLabel;
    @FXML private Label selectedDeviceLabel;
    @FXML private TextField oidField;
    @FXML private TextField valueField;
    @FXML private TextField subnetField;
    @FXML private TextField communityField;
    @FXML private TextArea resultArea;
    @FXML private ProgressIndicator scanProgress;
    @FXML private GridPane buttonGrid; // New reference for button grid

    @FXML private TextArea trapArea; // TextArea pour afficher les traps
    @FXML private Button startTrapButton; // Bouton pour démarrer l'écoute
    @FXML private Button stopTrapButton; // Bouton pour arrêter l'écoute
    private ObservableList<String> trapMessages = FXCollections.observableArrayList();

    private ObservableList<Device> devices = FXCollections.observableArrayList();
    private Device selectedDevice;
    private String community = "uy1";
    private final NetworkScanner networkScanner;
    private final SnmpClient snmpClient;

    public SnmpController() {
        this.snmpClient = new SnmpClient(1000, 2);
        this.networkScanner = new NetworkScanner(snmpClient);
    }

    @FXML
    public void initialize() {
        if (deviceList != null) {
            deviceList.setItems(devices);
            showNamesCheckBox.setSelected(true);
            deviceList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
                selectedDevice = newVal;
                updateSelectedDeviceLabel();
            });
            deviceList.setCellFactory(list -> new ListCell<Device>() {
                @Override
                protected void updateItem(Device device, boolean empty) {
                    super.updateItem(device, empty);
                    if (empty || device == null) {
                        setText(null);
                        setStyle("");
                    } else {
                        setText(showNamesCheckBox.isSelected() ? device.getName() : device.getIp());
                        setStyle("-fx-text-fill: green;");
                    }
                }
            });
            String subnet = networkScanner.detectLocalSubnet();
            if (subnet != null) {
                subnetField.setText(subnet);
            }
            addNetworkHost();

            // Bind GridPane spacing to window width
            if (buttonGrid != null && deviceList.getScene() != null) {
                buttonGrid.hgapProperty().bind(deviceList.getScene().widthProperty().divide(40));
                buttonGrid.vgapProperty().bind(deviceList.getScene().heightProperty().divide(30));
            }
        }
        updateSelectedDeviceLabel();

        if (trapArea != null) {
            trapArea.setEditable(false);
            trapArea.setWrapText(true);
            trapArea.setStyle("-fx-control-inner-background: black; -fx-text-fill: limegreen; -fx-font-family: 'Consolas'; -fx-font-size: 16px;");
            // Lier la TextArea à la liste des messages
            trapMessages.addListener((javafx.collections.ListChangeListener.Change<? extends String> c) -> {
                trapArea.setText(String.join("\n", trapMessages));
                trapArea.setScrollTop(Double.MAX_VALUE); // Auto-scroll
            });
        }

        // Configurer les boutons start/stop
        if (startTrapButton != null) {
            startTrapButton.setOnAction(e -> startTrapListener());
        }
        if (stopTrapButton != null) {
            stopTrapButton.setOnAction(e -> stopTrapListener());
        }
        stopTrapListener();
    }

    private void startTrapListener() {
        if (statusLabel != null) {
            statusLabel.setText("Démarrage de l'écouteur de traps...");
        }
        new Thread(() -> {
            try {
                // Use a non-privileged port, e.g., 1162
                snmpClient.startTrapListener(1162, trapMessage -> Platform.runLater(() -> {
                    trapMessages.add(trapMessage);
                    if (statusLabel != null) {
                        statusLabel.setText("Trap reçu");
                    }
                }));
                Platform.runLater(() -> {
                    if (statusLabel != null) {
                        statusLabel.setText("Écouteur de traps démarré sur le port 1162");
                    }
                });
            } catch (IOException e) {
                Platform.runLater(() -> {
                    if (statusLabel != null) {
                        statusLabel.setText("Erreur : " + e.getMessage());
                    }
                });
                System.err.println("Erreur démarrage trap listener: " + e.getMessage());
            }
        }).start();
    }

    private void stopTrapListener() {
        if (statusLabel != null) {
            statusLabel.setText("Arrêt de l'écouteur de traps...");
        }
        new Thread(() -> {
            try {
                snmpClient.stopTrapListener();
                Platform.runLater(() -> {
                    if (statusLabel != null) {
                        statusLabel.setText("Écouteur de traps arrêté");
                    }
                });
            } catch (IOException e) {
                Platform.runLater(() -> {
                    if (statusLabel != null) {
                        statusLabel.setText("Erreur : " + e.getMessage());
                    }
                });
                System.err.println("Erreur arrêt trap listener: " + e.getMessage());
            }
        }).start();
    }

    private void updateSelectedDeviceLabel() {
        if (selectedDeviceLabel != null) {
            selectedDeviceLabel.setText(selectedDevice != null ? selectedDevice.getName() : "Aucune machine sélectionnée");
        }
    }

    @FXML
    private void toggleNames() {
        deviceList.refresh();
    }

    @FXML
    private void scanNetwork() {
        if (scanProgress != null) {
            scanProgress.setVisible(true);
        }
        statusLabel.setText("Scan du réseau en cours...");

        String subnet = subnetField.getText();
        if (subnet == null || subnet.trim().isEmpty()) {
            subnet = networkScanner.detectLocalSubnet();
            if (subnet == null) {
                statusLabel.setText("Erreur : Impossible de détecter le sous-réseau.");
                scanProgress.setVisible(false);
                addNetworkHost();
                return;
            }
            subnetField.setText(subnet);
        }

        community = communityField != null && !communityField.getText().trim().isEmpty() ?
                communityField.getText().trim() : "uy1";

        networkScanner.scanNetwork(subnet, community, new NetworkScanCallback() {
            @Override
            public void onProgress(int current, int total) {
                Platform.runLater(() -> statusLabel.setText(String.format("Scan en cours : %d/%d adresses", current, total)));
            }

            @Override
            public void onComplete(List<Device> scannedDevices) {
                Platform.runLater(() -> {
                    devices.setAll(scannedDevices);
                    scanProgress.setVisible(false);
                    statusLabel.setText(scannedDevices.size() == 1 && scannedDevices.get(0).getName().equals("Hôte Réseau") ?
                            "Scan terminé : seul l'hôte réseau trouvé." :
                            "Scan terminé : " + scannedDevices.size() + " appareils trouvés.");
                    System.out.println("Scan terminé - Devices: " + devices);
                });
            }

            @Override
            public void onError(String message) {
                Platform.runLater(() -> {
                    statusLabel.setText("Erreur : " + message);
                    scanProgress.setVisible(false);
                    addNetworkHost();
                });
            }
        });
    }

    private void addNetworkHost() {
        Device host = networkScanner.getNetworkHost(community);
        if (host != null) {
            devices.setAll(host);
            statusLabel.setText("Hôte réseau ajouté.");
            System.out.println("Hôte ajouté - Device: " + host);
        }
    }

    @FXML
    private void executeGet() {
        if (selectedDevice == null || oidField == null || oidField.getText().isEmpty()) {
            resultArea.setText("Veuillez sélectionner une machine et entrer un OID.");
            statusLabel.setText("Erreur : paramètres manquants");
            System.out.println("Erreur : selectedDevice=" + selectedDevice + ", OID=" + (oidField != null ? oidField.getText() : "null"));
            return;
        }

        String oid = oidField.getText().trim();
        if (!oid.matches("^\\.?[0-9.]+$")) {
            resultArea.setText("Erreur : Format d'OID invalide (ex: 1.3.6.1.2.1.1.1.0).");
            statusLabel.setText("Erreur : OID invalide");
            return;
        }

        statusLabel.setText("Envoi de la requête SNMP GET...");
        resultArea.setText("Requête en cours pour " + selectedDevice.getName() + " (IP: " + selectedDevice.getIp() + ") avec communauté " + community + "...");

        new Thread(() -> {
            String result;
            try {
                result = snmpClient.performGet(selectedDevice.getIp(), oid, community);
            } catch (IOException e) {
                result = null;
                System.err.println("Erreur SNMP: " + e.getMessage());
            }
            String finalResult = result;
            Platform.runLater(() -> {
                if (finalResult != null) {
//                    resultArea.setText("Résultat pour " + selectedDevice.getName() + " (OID " + oid + "):\n" + finalResult);
                    resultArea.setText(finalResult);
                    statusLabel.setText("Requête réussie");
                } else {
                    resultArea.setText("Erreur : Aucune réponse pour l'OID " + oid + " sur " + selectedDevice.getName() + " (IP: " + selectedDevice.getIp() + ") avec communauté " + community);
                    statusLabel.setText("Erreur : Requête échouée");
                }
            });
        }).start();
    }

    @FXML
    private void navigateToGet() {
        navigateTo("get.fxml", "Opération SNMP : Get");
    }


    @FXML
    private void executeGetNext() {
        if (selectedDevice == null || oidField.getText().isEmpty()) {
            resultArea.setText("Sélectionnez une machine et entrez un OID.");
            return;
        }

        String oid = oidField.getText().trim();
        statusLabel.setText("Requête GETNEXT en cours...");
        resultArea.setText("Requête GETNEXT en cours...");

        new Thread(() -> {
            String result = null;
            try {
                result = snmpClient.performGetNext(selectedDevice.getIp(), oid, community);
            } catch (IOException e) {
                System.err.println("Erreur GETNEXT: " + e.getMessage());
            }

            final String output = result;
            Platform.runLater(() -> {
                if (output != null) {
                    resultArea.setText(output);
                    statusLabel.setText("GETNEXT réussi");
                } else {
                    resultArea.setText("Aucune réponse.");
                    statusLabel.setText("GETNEXT échoué");
                }
            });
        }).start();
    }

    @FXML
    private void navigateToGetNext() {
        navigateTo("getNext.fxml", "Opération SNMP : GetNext");
    }


    @FXML
    private void executeSetMessage() {
        if (selectedDevice == null || oidField.getText().isEmpty() || valueField.getText().isEmpty()) {
            resultArea.setText("Veuillez remplir l'OID et la valeur.");
            return;
        }

        String oid = oidField.getText().trim();
        String value = valueField.getText().trim();
        resultArea.setText("Envoi de Set...");

        new Thread(() -> {
            boolean success = false;
            try {
                success = snmpClient.performSet(selectedDevice.getIp(), oid, value, community);
            } catch (IOException e) {
                System.err.println("Erreur Set: " + e.getMessage());
            }

            boolean finalSuccess = success;
            Platform.runLater(() -> {
                resultArea.setText(finalSuccess ? "Set réussi." : "Set échoué.");
                statusLabel.setText(finalSuccess ? "OK" : "Erreur");
            });
        }).start();
    }

    @FXML
    private void navigateToSet() {
        navigateTo("set.fxml", "Opération SNMP : Set");
    }


    @FXML
    private void executeStat() {
        if (selectedDevice == null) {
            resultArea.setText("Veuillez sélectionner une machine.");
            return;
        }

        resultArea.setText("Récupération des statistiques...");
        new Thread(() -> {
            List<String> stats = null;
            try {
                stats = snmpClient.performStat(selectedDevice.getIp(), community);
            } catch (IOException e) {
                System.err.println("Erreur Stat: " + e.getMessage());
            }

            List<String> finalStats = stats;
            Platform.runLater(() -> {
                if (finalStats != null) {
                    resultArea.setText(String.join("\n", finalStats));
                    statusLabel.setText("Statistiques reçues");
                } else {
                    resultArea.setText("Aucune donnée.");
                    statusLabel.setText("Erreur Stat");
                }
            });
        }).start();
    }

    @FXML
    private void navigateToStat() {
        navigateTo("stat.fxml", "Opération SNMP : Stat");
    }



    @FXML
    private void executeWalk() {
        if (selectedDevice == null || oidField == null || oidField.getText().isEmpty()) {
            resultArea.setText("Veuillez sélectionner une machine et entrer un OID.");
            statusLabel.setText("Erreur : paramètres manquants");
            return;
        }

        String oid = oidField.getText().trim();
        if (!oid.matches("^\\.?[0-9.]+$")) {
            resultArea.setText("Erreur : Format d'OID invalide (ex: 1.3.6.1.2.1.1).");
            statusLabel.setText("Erreur : OID invalide");
            return;
        }

        statusLabel.setText("Envoi de la requête SNMP WALK...");
        resultArea.setText("Requête en cours pour " + selectedDevice.getName() + " (IP: " + selectedDevice.getIp() + ") avec communauté " + community + "...");

        new Thread(() -> {
            List<String> results;
            try {
                results = snmpClient.performWalk(selectedDevice.getIp(), oid, community);
            } catch (IOException e) {
                results = null;
                System.err.println("Erreur SNMP Walk: " + e.getMessage());
            }

            List<String> finalResults = results;
            Platform.runLater(() -> {
                if (finalResults != null && !finalResults.isEmpty()) {
                    resultArea.setText(String.join("\n", finalResults));
                    statusLabel.setText("Requête Walk réussie");
                } else {
                    resultArea.setText("Erreur : Aucune réponse ou fin du Walk.");
                    statusLabel.setText("Erreur : Requête Walk échouée");
                }
            });
        }).start();
    }

    @FXML
    private void navigateToWalk() {
        navigateTo("walk.fxml", "Opération SNMP : Walk");
    }



    @FXML
    private void navigateToTrap() {
        navigateTo("trap.fxml", "Opération SNMP : Trap");
    }

    @FXML
    private void navigateToHome() {
        navigateTo("home.fxml", "Administrateur Réseau SNMP");
    }

    private void navigateTo(String fxmlFile, String title) {
        if (selectedDevice == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Avertissement");
            alert.setHeaderText("Aucune machine sélectionnée");
            alert.setContentText("Veuillez sélectionner une machine avant de continuer.");
            alert.showAndWait();
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/tp_216_snmp/frontend/" + fxmlFile));
            Scene scene = new Scene(loader.load(), 1200, 900);
            // Get the stage from any available node or the current scene
            Stage stage = null;
            if (deviceList != null && deviceList.getScene() != null) {
                stage = (Stage) deviceList.getScene().getWindow();
            } else if (resultArea != null && resultArea.getScene() != null) {
                stage = (Stage) resultArea.getScene().getWindow();
            } else if (statusLabel != null && statusLabel.getScene() != null) {
                stage = (Stage) statusLabel.getScene().getWindow();
            }
            if (stage == null) {
                throw new IllegalStateException("Unable to find the current stage.");
            }
            stage.setScene(scene);
            stage.setTitle(title);
            SnmpController controller = loader.getController();
            controller.setSelectedDevice(selectedDevice);
            controller.setCommunity(community);
        } catch (IOException e) {
            if (statusLabel != null) {
                statusLabel.setText("Erreur : Impossible de charger " + fxmlFile);
            }
            System.err.println("Erreur de navigation: " + e.getMessage());
        }
    }

    public void setSelectedDevice(Device device) {
        this.selectedDevice = device;
        updateSelectedDeviceLabel();
    }

    public void setCommunity(String community) {
        this.community = community != null && !community.trim().isEmpty() ? community.trim() : "uy1";
        if (communityField != null) {
            communityField.setText(this.community);
        }
    }

    @FXML
    private void executeSet() {
        if (selectedDevice == null || oidField == null || oidField.getText().isEmpty() || valueField == null || valueField.getText().isEmpty()) {
            resultArea.setText("Veuillez sélectionner une machine, entrer un OID et une valeur.");
            statusLabel.setText("Erreur : paramètres manquants");
            return;
        }
        resultArea.setText("Exécution de Set sur " + selectedDevice.getName() + " avec OID " + oidField.getText() + " et valeur " + valueField.getText());
        statusLabel.setText("Requête envoyée...");
    }
}
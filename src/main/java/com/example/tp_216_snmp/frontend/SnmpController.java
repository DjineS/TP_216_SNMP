package com.example.tp_216_snmp.frontend;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultUdpTransportMapping;

public class SnmpController {
    @FXML private ListView<String> deviceList;
    @FXML private CheckBox showNamesCheckBox;
    @FXML private Label statusLabel;
    @FXML private Label selectedDeviceLabel;
    @FXML private TextField oidField;
    @FXML private TextField valueField;
    @FXML private TextField subnetField;
    @FXML private TextArea resultArea;
    @FXML private ProgressIndicator scanProgress;

    private ObservableList<String> machineNames = FXCollections.observableArrayList();
    private ObservableList<String> machineIPs = FXCollections.observableArrayList();
    private String selectedDevice;

    @FXML
    public void initialize() {
        if (deviceList != null) {
            deviceList.setItems(machineNames);
            showNamesCheckBox.setSelected(true);
            deviceList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
                selectedDevice = newVal;
                updateSelectedDeviceLabel();
            });
            deviceList.setCellFactory(list -> new ListCell<String>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setStyle("");
                    } else {
                        setText(item);
                        setStyle("-fx-text-fill: green;");
                    }
                }
            });
            String subnet = detectLocalSubnet();
            if (subnet != null) {
                subnetField.setText(subnet);
            }
            addNetworkHost();
        }
        updateSelectedDeviceLabel();
    }

    private void updateSelectedDeviceLabel() {
        if (selectedDeviceLabel != null) {
            selectedDeviceLabel.setText(selectedDevice != null ? selectedDevice : "Aucune machine sélectionnée");
        }
    }

    @FXML
    private void toggleNames() {
        if (showNamesCheckBox.isSelected()) {
            deviceList.setItems(machineNames);
        } else {
            deviceList.setItems(machineIPs);
        }
        deviceList.getSelectionModel().clearSelection();
    }

    @FXML
    private void scanNetwork() {
        if (scanProgress != null) {
            scanProgress.setVisible(true);
        }
        statusLabel.setText("Scan du réseau en cours...");

        String subnetInput = subnetField.getText();
        if (subnetInput == null || subnetInput.trim().isEmpty()) {
            subnetInput = detectLocalSubnet();
            if (subnetInput == null) {
                statusLabel.setText("Erreur : Impossible de détecter le sous-réseau.");
                scanProgress.setVisible(false);
                addNetworkHost();
                return;
            }
            subnetField.setText(subnetInput);
        }

        String[] parts = subnetInput.split("/");
        if (parts.length != 2) {
            statusLabel.setText("Erreur : Format de sous-réseau invalide (ex: 192.168.1.0/24).");
            scanProgress.setVisible(false);
            addNetworkHost();
            return;
        }
        String baseIp = parts[0];
        int prefixLength;
        try {
            prefixLength = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            statusLabel.setText("Erreur : Masque de sous-réseau invalide.");
            scanProgress.setVisible(false);
            addNetworkHost();
            return;
        }

        List<String> ipRange = calculateIpRange(baseIp, prefixLength);
        if (ipRange.isEmpty()) {
            statusLabel.setText("Erreur : Plage d'IP invalide.");
            scanProgress.setVisible(false);
            addNetworkHost();
            return;
        }

        ExecutorService executor = Executors.newFixedThreadPool(10);
        List<String> discoveredIPs = new ArrayList<>();
        List<String> discoveredNames = new ArrayList<>();
        int total = ipRange.size();
        int[] current = {0};

        String[] hostInfo = getNetworkHostInfo();
        if (hostInfo != null) {
            synchronized (discoveredIPs) {
                discoveredIPs.add(hostInfo[0]);
                discoveredNames.add(hostInfo[1]);
            }
        }

        for (String ip : ipRange) {
            if (hostInfo != null && ip.equals(hostInfo[0])) continue;

            executor.submit(() -> {
                String name = getDeviceName(ip);
                if (name != null) {
                    synchronized (discoveredIPs) {
                        discoveredIPs.add(ip);
                        discoveredNames.add(name.isEmpty() ? ip : name);
                    }
                }
                synchronized (current) {
                    current[0]++;
                    Platform.runLater(() -> statusLabel.setText(String.format("Scan en cours : %d/%d adresses", current[0], total)));
                }
            });
        }

        executor.shutdown();
        new Thread(() -> {
            try {
                executor.awaitTermination(60, TimeUnit.SECONDS);
                Platform.runLater(() -> {
                    synchronized (machineIPs) {
                        machineIPs.setAll(discoveredIPs);
                        machineNames.setAll(discoveredNames);
                    }
                    if (showNamesCheckBox.isSelected()) {
                        deviceList.setItems(machineNames);
                    } else {
                        deviceList.setItems(machineIPs);
                    }
                    scanProgress.setVisible(false);
                    statusLabel.setText(discoveredIPs.size() == 1 && hostInfo != null ?
                            "Scan terminé : seul l'hôte réseau trouvé." :
                            "Scan terminé : " + discoveredIPs.size() + " appareils trouvés.");
                    System.out.println("Scan terminé - IPs: " + machineIPs + ", Names: " + machineNames);
                });
            } catch (InterruptedException e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Erreur : Scan interrompu.");
                    scanProgress.setVisible(false);
                });
            }
        }).start();
    }

    private void addNetworkHost() {
        String[] hostInfo = getNetworkHostInfo();
        if (hostInfo != null) {
            synchronized (machineIPs) {
                machineIPs.setAll(hostInfo[0]);
                machineNames.setAll(hostInfo[1]);
            }
            if (showNamesCheckBox.isSelected()) {
                deviceList.setItems(machineNames);
            } else {
                deviceList.setItems(machineIPs);
            }
            statusLabel.setText("Hôte réseau ajouté.");
            System.out.println("Hôte ajouté - IPs: " + machineIPs + ", Names: " + machineNames);
        }
    }

    private String[] getNetworkHostInfo() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) continue;
                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    InetAddress addr = ia.getAddress();
                    if (addr.isLoopbackAddress() || addr instanceof java.net.Inet6Address) continue;
                    String ip = addr.getHostAddress();
                    String name = getDeviceName(ip);
                    if (name == null) {
                        name = addr.getHostName();
                        if (name.equals(ip)) name = "Hôte Réseau";
                    }
                    System.out.println("Host IP: " + ip + ", Name: " + name);
                    return new String[]{ip, name.isEmpty() ? ip : name};
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return null;
    }

    private String detectLocalSubnet() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) continue;
                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    InetAddress addr = ia.getAddress();
                    if (addr.isLoopbackAddress() || addr instanceof java.net.Inet6Address) continue;
                    String ip = addr.getHostAddress();
                    int prefixLength = ia.getNetworkPrefixLength();
                    String[] parts = ip.split("\\.");
                    if (parts.length == 4) {
                        int[] ipParts = new int[4];
                        for (int i = 0; i < 4; i++) {
                            ipParts[i] = Integer.parseInt(parts[i]);
                        }
                        int mask = -1 << (32 - prefixLength);
                        StringBuilder network = new StringBuilder();
                        for (int i = 0; i < 4; i++) {
                            network.append((ipParts[i] & (mask >>> (24 - 8 * i))) & 0xFF);
                            if (i < 3) network.append(".");
                        }
                        return network.toString() + "/" + prefixLength;
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return null;
    }

    private List<String> calculateIpRange(String baseIp, int prefixLength) {
        List<String> ipRange = new ArrayList<>();
        try {
            String[] parts = baseIp.split("\\.");
            if (parts.length != 4) return ipRange;
            int[] ipParts = new int[4];
            for (int i = 0; i < 4; i++) {
                ipParts[i] = Integer.parseInt(parts[i]);
            }
            int mask = -1 << (32 - prefixLength);
            long network = ((ipParts[0] & 0xFFL) << 24) | ((ipParts[1] & 0xFFL) << 16) |
                    ((ipParts[2] & 0xFFL) << 8) | (ipParts[3] & 0xFFL);
            network &= mask;
            long broadcast = network | (~mask & 0xFFFFFFFFL);
            for (long ip = network + 1; ip < broadcast; ip++) {
                ipRange.add(String.format("%d.%d.%d.%d",
                        (ip >> 24) & 0xFF, (ip >> 16) & 0xFF, (ip >> 8) & 0xFF, ip & 0xFF));
            }
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }
        return ipRange;
    }

    private String getDeviceName(String ip) {
        return performSnmpGet(ip, "1.3.6.1.2.1.1.5.0"); // sysName.0
    }

    private String performSnmpGet(String ip, String oid) {
        try {
            System.out.println("SNMP GET: IP=" + ip + ", OID=" + oid);
            Snmp snmp = new Snmp(new DefaultUdpTransportMapping());
            snmp.listen();

            CommunityTarget target = new CommunityTarget();
            target.setCommunity(new OctetString("uy1"));
            target.setAddress(new UdpAddress(InetAddress.getByName(ip), 161));
            target.setVersion(SnmpConstants.version2c);
            target.setTimeout(1000);
            target.setRetries(2);

            PDU pdu = new PDU();
            pdu.add(new VariableBinding(new OID(oid)));
            pdu.setType(PDU.GET);

            ResponseEvent response = snmp.send(pdu, target);
            snmp.close();

            if (response != null && response.getResponse() != null && response.getResponse().getErrorStatus() == PDU.noError) {
                String value = response.getResponse().get(0).getVariable().toString();
                System.out.println("Response from " + ip + ": " + value);
                return value.isEmpty() ? ip : value;
            } else {
                String error = response != null && response.getResponse() != null ?
                        response.getResponse().getErrorStatusText() : "Timeout";
                System.out.println("No response from " + ip + ": " + error);
                return null;
            }
        } catch (IOException e) {
            System.out.println("Error performing SNMP GET on " + ip + ": " + e.getMessage());
            return null;
        }
    }

    private String getIpForSelectedDevice() {
        if (selectedDevice == null) {
            System.out.println("Erreur : selectedDevice est null");
            return null;
        }
        System.out.println("selectedDevice: " + selectedDevice);
        System.out.println("machineNames: " + machineNames);
        System.out.println("machineIPs: " + machineIPs);

        int index = machineNames.indexOf(selectedDevice);
        if (index >= 0 && index < machineIPs.size()) {
            String ip = machineIPs.get(index);
            System.out.println("Résolution nom -> IP: " + selectedDevice + " -> " + ip);
            return ip;
        }

        if (machineIPs.contains(selectedDevice)) {
            System.out.println("selectedDevice est déjà une IP: " + selectedDevice);
            return selectedDevice;
        }

        try {
            InetAddress.getByName(selectedDevice);
            if (machineNames.contains(selectedDevice)) {
                index = machineNames.indexOf(selectedDevice);
                if (index >= 0 && index < machineIPs.size()) {
                    String ip = machineIPs.get(index);
                    System.out.println("Résolution via machineNames: " + selectedDevice + " -> " + ip);
                    return ip;
                }
            }
            System.out.println("selectedDevice est une IP valide mais non listée: " + selectedDevice);
            return selectedDevice;
        } catch (IOException e) {
            System.out.println("Erreur : selectedDevice n'est ni un nom ni une IP valide: " + selectedDevice);
            return null;
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

        String ip = getIpForSelectedDevice();
        if (ip == null) {
            resultArea.setText("Erreur : Impossible de résoudre l'adresse IP de la machine sélectionnée (" + selectedDevice + ").");
            statusLabel.setText("Erreur : IP invalide");
            return;
        }

        String oid = oidField.getText().trim();
        if (!oid.matches("^\\.?[0-9.]+$")) {
            resultArea.setText("Erreur : Format d'OID invalide (ex: 1.3.6.1.2.1.1.1.0).");
            statusLabel.setText("Erreur : OID invalide");
            return;
        }

        statusLabel.setText("Envoi de la requête SNMP GET...");
        resultArea.setText("Requête en cours pour " + selectedDevice + " (IP: " + ip + ")...");

        new Thread(() -> {
            String result = performSnmpGet(ip, oid);
            Platform.runLater(() -> {
                if (result != null) {
                    resultArea.setText("Résultat pour " + selectedDevice + " (OID " + oid + "):\n" + result);
                    statusLabel.setText("Requête réussie");
                } else {
                    resultArea.setText("Erreur : Aucune réponse pour l'OID " + oid + " sur " + selectedDevice + " (IP: " + ip + ")");
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
    private void navigateToGetNext() {
        navigateTo("getNext.fxml", "Opération SNMP : GetNext");
    }

    @FXML
    private void navigateToSet() {
        navigateTo("set.fxml", "Opération SNMP : Set");
    }

    @FXML
    private void navigateToStat() {
        navigateTo("stat.fxml", "Opération SNMP : Stat");
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
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/tp_216_snmp/frontend/" + fxmlFile));
            Scene scene = new Scene(loader.load(), 800, 600);
            Stage stage = (Stage) (deviceList != null ? deviceList.getScene().getWindow() : resultArea.getScene().getWindow());
            stage.setScene(scene);
            stage.setTitle(title);
            SnmpController controller = loader.getController();
            if (selectedDevice != null) {
                controller.setSelectedDevice(selectedDevice);
            }
        } catch (IOException e) {
            if (statusLabel != null) {
                statusLabel.setText("Erreur : Impossible de charger " + fxmlFile);
            }
            e.printStackTrace();
        }
    }

    public void setSelectedDevice(String device) {
        this.selectedDevice = device;
        updateSelectedDeviceLabel();
    }

    @FXML
    private void executeSet() {
        if (selectedDevice != null && oidField != null && !oidField.getText().isEmpty() && valueField != null && !valueField.getText().isEmpty()) {
            resultArea.setText("Exécution de Set sur " + selectedDevice + " avec OID " + oidField.getText() + " et valeur " + valueField.getText());
            statusLabel.setText("Requête envoyée...");
        } else {
            resultArea.setText("Veuillez sélectionner une machine, entrer un OID et une valeur.");
            statusLabel.setText("Erreur : paramètres manquants");
        }
    }
}
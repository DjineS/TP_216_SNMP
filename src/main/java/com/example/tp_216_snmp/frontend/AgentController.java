package com.example.tp_216_snmp.frontend;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.TransportMapping;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.*;
import org.snmp4j.transport.DefaultUdpTransportMapping;

import java.io.IOException;
import java.util.regex.Pattern;

public class AgentController {

    @FXML
    private TextField trapDestinationField;
    @FXML
    private TextField communityField;
    @FXML
    private TextField oidField;
    @FXML
    private TextField messageField;
    @FXML
    private Button trapButton;
    @FXML
    private Label statusLabel;

    private static final Pattern IP_PATTERN = Pattern.compile(
            "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    );

    @FXML
    private void initialize() {
        trapDestinationField.setText("192.168.1.150");
        communityField.setText("uy1");
        oidField.setText("1.3.6.1.6.3.1.1.5.1");
    }

    @FXML
    private void sendTrap() {
        String destination = trapDestinationField.getText().trim();
        String community = communityField.getText().trim().isEmpty() ? "uy1" : communityField.getText().trim();
        String oid = oidField.getText().trim();
        String message = messageField.getText().trim();

        // Valider l'adresse IP
        if (!IP_PATTERN.matcher(destination).matches()) {
            statusLabel.setText("Adresse IP invalide. Exemple : 192.168.1.150");
            return;
        }

        // Valider l'OID
        try {
            new OID(oid);
        } catch (IllegalArgumentException e) {
            statusLabel.setText("OID invalide. Exemple : 1.3.6.1.6.3.1.1.5.1");
            return;
        }

        try (TransportMapping<UdpAddress> transport = new DefaultUdpTransportMapping()) {
            // Configurer le transport
            Snmp snmp = new Snmp(transport);
            transport.listen();

            // Configurer la cible
            CommunityTarget<Address> target = new CommunityTarget<>();
            target.setCommunity(new OctetString(community));
            target.setAddress(GenericAddress.parse("udp:" + destination + "/1162"));
            target.setVersion(SnmpConstants.version2c);
            target.setTimeout(1500);
            target.setRetries(2);

            // Créer un trap
            PDU pdu = new PDU();
            pdu.setType(PDU.TRAP);
            long sysUpTime = (System.currentTimeMillis() / 10) % 4294967296L; // Limiter à 32 bits non signés
            pdu.add(new VariableBinding(new OID("1.3.6.1.2.1.1.3.0"), new TimeTicks(sysUpTime)));
            pdu.add(new VariableBinding(new OID("1.3.6.1.6.3.1.1.4.1.0"), new OID(oid)));
            if (!message.isEmpty()) {
                pdu.add(new VariableBinding(new OID("1.3.6.1.2.1.1.1.0"), new OctetString(message)));
            }

            // Envoyer le trap
            snmp.send(pdu, target);
            statusLabel.setText("Trap envoyé à " + destination + " avec OID " + oid);
        } catch (IOException e) {
            statusLabel.setText("Erreur lors de l'envoi du trap : " + e.getMessage());
        }
    }
}
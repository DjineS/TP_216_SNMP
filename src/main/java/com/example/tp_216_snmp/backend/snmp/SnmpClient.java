package com.example.tp_216_snmp.backend.snmp;

import javafx.collections.ObservableList;
import org.snmp4j.*;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.MPv1;
import org.snmp4j.mp.MPv2c;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.security.SecurityModels;
import org.snmp4j.security.SecurityProtocols;
import org.snmp4j.security.USM;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.snmp4j.util.MultiThreadedMessageDispatcher;
import org.snmp4j.util.ThreadPool;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class SnmpClient {
    private final int timeout;
    private final int retries;

    public SnmpClient(int timeout, int retries) {
        this.timeout = timeout;
        this.retries = retries;
    }

    private Snmp snmpTrapReceiver;
    private ThreadPool threadPool;

    private ObservableList<String> trapMessages;

    public String performGet(String ip, String oid, String community) throws IOException {
        System.out.println("SNMP GET: IP=" + ip + ", OID=" + oid + ", Community=" + community);
        try (Snmp snmp = new Snmp(new DefaultUdpTransportMapping())) {
            snmp.listen();

            CommunityTarget target = new CommunityTarget();
            target.setCommunity(new OctetString(community));
            target.setAddress(new UdpAddress(InetAddress.getByName(ip), 161));
            target.setVersion(SnmpConstants.version2c);
            target.setTimeout(timeout);
            target.setRetries(retries);

            PDU pdu = new PDU();
            pdu.add(new VariableBinding(new OID(oid)));
            pdu.setType(PDU.GET);

            ResponseEvent response = snmp.send(pdu, target);
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
        }
    }


    public List<String> performWalk(String ip, String rootOid, String community) throws IOException {
        List<String> results = new ArrayList<>();
        System.out.println("SNMP WALK: IP=" + ip + ", RootOID=" + rootOid + ", Community=" + community);
        try (Snmp snmp = new Snmp(new DefaultUdpTransportMapping())) {
            snmp.listen();

            CommunityTarget target = new CommunityTarget();
            target.setCommunity(new OctetString(community));
            target.setAddress(new UdpAddress(InetAddress.getByName(ip), 161));
            target.setVersion(SnmpConstants.version2c);
            target.setTimeout(timeout);
            target.setRetries(retries);

            OID rootOID = new OID(rootOid);
            OID currentOID = rootOID;

            while (true) {
                PDU pdu = new PDU();
                pdu.add(new VariableBinding(currentOID));
                pdu.setType(PDU.GETNEXT);

                ResponseEvent response = snmp.send(pdu, target);
                if (response == null || response.getResponse() == null) {
                    System.out.println("Fin du walk ou aucune réponse.");
                    break;
                }

                VariableBinding vb = response.getResponse().get(0);
                OID nextOID = vb.getOid();

                if (nextOID == null || !nextOID.startsWith(rootOID) || nextOID.compareTo(currentOID) <= 0) {
                    // Fin de la branche ou OID non descendant
                    break;
                }

                String line = nextOID.toDottedString() + " = " + vb.getVariable();
                System.out.println(line);
                results.add(line);

                currentOID = nextOID;
            }
        }
        return results;
    }


    // ... (tes imports et autres méthodes)

    public String performGetNext(String ip, String oid, String community) throws IOException {
        System.out.println("SNMP GET-NEXT: IP=" + ip + ", OID=" + oid + ", Community=" + community);
        try (Snmp snmp = new Snmp(new DefaultUdpTransportMapping())) {
            snmp.listen();

            CommunityTarget target = new CommunityTarget();
            target.setCommunity(new OctetString(community));
            target.setAddress(new UdpAddress(InetAddress.getByName(ip), 161));
            target.setVersion(SnmpConstants.version2c);
            target.setTimeout(timeout);
            target.setRetries(retries);

            PDU pdu = new PDU();
            pdu.add(new VariableBinding(new OID(oid)));
            pdu.setType(PDU.GETNEXT);

            ResponseEvent response = snmp.send(pdu, target);
            if (response != null && response.getResponse() != null) {
                VariableBinding vb = response.getResponse().get(0);
                return vb.getOid() + " = " + vb.getVariable();
            } else {
                return null;
            }
        }
    }

    public boolean performSet(String ip, String oid, String value, String community) throws IOException {
        System.out.println("SNMP SET: IP=" + ip + ", OID=" + oid + ", Value=" + value + ", Community=" + community);
        try (Snmp snmp = new Snmp(new DefaultUdpTransportMapping())) {
            snmp.listen();

            CommunityTarget target = new CommunityTarget();
            target.setCommunity(new OctetString(community));
            target.setAddress(new UdpAddress(InetAddress.getByName(ip), 161));
            target.setVersion(SnmpConstants.version2c);
            target.setTimeout(timeout);
            target.setRetries(retries);

            PDU pdu = new PDU();
            pdu.add(new VariableBinding(new OID(oid), new OctetString(value)));
            pdu.setType(PDU.SET);

            ResponseEvent response = snmp.send(pdu, target);
            return response != null && response.getResponse() != null &&
                    response.getResponse().getErrorStatus() == PDU.noError;
        }
    }

    public List<String> performStat(String ip, String community) throws IOException {
        // Exemple : récupérer les statistiques systèmes
        return performWalk(ip, "1.3.6.1.2.1.1", community); // OID standard pour system
    }

    public void startTrapListener(int trapPort, Consumer<String> trapConsumer) throws IOException {
        if (snmpTrapReceiver != null) {
            System.out.println("Trap listener already running.");
            return;
        }

        // Créer un pool de threads pour gérer les traps
        threadPool = ThreadPool.create("Trap", 2);
        MultiThreadedMessageDispatcher dispatcher = new MultiThreadedMessageDispatcher(threadPool, new MessageDispatcherImpl());

        // Ajouter les versions SNMP supportées
        dispatcher.addMessageProcessingModel(new MPv1());
        dispatcher.addMessageProcessingModel(new MPv2c());

        // Configurer la sécurité (optionnel pour v2c)
        SecurityProtocols.getInstance().addDefaultProtocols();
        SecurityModels.getInstance().addSecurityModel(new USM());

        // Configurer le transport (UDP par défaut)
        TransportMapping<?> transport = new DefaultUdpTransportMapping(new UdpAddress(trapPort));
        snmpTrapReceiver = new Snmp(dispatcher, transport);

        // Ajouter un CommandResponder pour traiter les traps
        snmpTrapReceiver.addCommandResponder(new CommandResponder() {
            @Override
            public void processPdu(CommandResponderEvent event) {
                PDU pdu = event.getPDU();
                if (pdu != null) {
                    String trapMessage = "Trap reçu de " + event.getPeerAddress() + ": " + pdu.toString();
                    System.out.println(trapMessage);
                    trapConsumer.accept(trapMessage);
                }
            }
        });

        // Démarrer l'écoute
        transport.listen();
        System.out.println("Trap listener démarré sur le port " + trapPort);
    }

    /**
     * Arrête l'écouteur de traps.
     * @throws IOException En cas d'erreur lors de l'arrêt.
     */
    public void stopTrapListener() throws IOException {
        if (snmpTrapReceiver != null) {
            snmpTrapReceiver.close();
            snmpTrapReceiver = null;
        }
        if (threadPool != null) {
            threadPool.cancel();
            threadPool = null;
        }
        System.out.println("Trap listener arrêté.");
    }

}
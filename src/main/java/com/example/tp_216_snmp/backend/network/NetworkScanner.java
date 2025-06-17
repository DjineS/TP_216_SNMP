package com.example.tp_216_snmp.backend.network;

import com.example.tp_216_snmp.backend.model.Device;
import com.example.tp_216_snmp.backend.snmp.SnmpClient;

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

public class NetworkScanner {
    private final SnmpClient snmpClient;

    public NetworkScanner(SnmpClient snmpClient) {
        this.snmpClient = snmpClient;
    }

    public Device getNetworkHost(String community) {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) continue;
                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    InetAddress addr = ia.getAddress();
                    if (addr.isLoopbackAddress() || addr instanceof java.net.Inet6Address) continue;
                    String ip = addr.getHostAddress();
                    String name = getDeviceName(ip, community);
                    if (name == null) {
                        name = addr.getHostName();
                        if (name.equals(ip)) name = "Hôte Réseau";
                    }
                    System.out.println("Host IP: " + ip + ", Name: " + name);
                    return new Device(ip, name);
                }
            }
        } catch (SocketException e) {
            System.err.println("Erreur lors de la détection de l'hôte réseau: " + e.getMessage());
        }
        return null;
    }

    public String detectLocalSubnet() {
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
            System.err.println("Erreur lors de la détection du sous-réseau: " + e.getMessage());
        }
        return null;
    }

    public void scanNetwork(String subnet, String community, NetworkScanCallback callback) {
        List<Device> devices = new ArrayList<>();
        String[] parts = subnet.split("/");
        if (parts.length != 2) {
            callback.onError("Format de sous-réseau invalide (ex: 192.168.1.0/24)");
            return;
        }
        String baseIp = parts[0];
        int prefixLength;
        try {
            prefixLength = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            callback.onError("Masque de sous-réseau invalide");
            return;
        }

        List<String> ipRange = calculateIpRange(baseIp, prefixLength);
        if (ipRange.isEmpty()) {
            callback.onError("Plage d'IP invalide");
            return;
        }

        Device host = getNetworkHost(community);
        if (host != null) {
            devices.add(host);
        }

        ExecutorService executor = Executors.newFixedThreadPool(10);
        int total = ipRange.size();
        int[] current = {0};

        for (String ip : ipRange) {
            if (host != null && ip.equals(host.getIp())) continue;

            executor.submit(() -> {
                String name = getDeviceName(ip, community);
                if (name != null) {
                    synchronized (devices) {
                        devices.add(new Device(ip, name));
                    }
                }
                synchronized (current) {
                    current[0]++;
                    callback.onProgress(current[0], total);
                }
            });
        }

        executor.shutdown();
        new Thread(() -> {
            try {
                executor.awaitTermination(60, TimeUnit.SECONDS);
                callback.onComplete(devices);
            } catch (InterruptedException e) {
                callback.onError("Scan interrompu");
            }
        }).start();
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
            System.err.println("Erreur lors du calcul de la plage IP: " + e.getMessage());
        }
        return ipRange;
    }

    private String getDeviceName(String ip, String community) {
        try {
            return snmpClient.performGet(ip, "1.3.6.1.2.1.1.5.0", community); // sysName.0
        } catch (IOException e) {
            System.err.println("Erreur SNMP pour IP " + ip + ": " + e.getMessage());
            return null;
        }
    }
}
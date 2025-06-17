package com.example.tp_216_snmp.backend.model;

public class Device {
    private final String ip;
    private final String name;

    public Device(String ip, String name) {
        this.ip = ip;
        this.name = name != null && !name.isEmpty() ? name : ip;
    }

    public String getIp() {
        return ip;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }
}
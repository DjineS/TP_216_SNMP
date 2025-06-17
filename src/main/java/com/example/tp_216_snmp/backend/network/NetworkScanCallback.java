package com.example.tp_216_snmp.backend.network;

import com.example.tp_216_snmp.backend.model.Device;
import java.util.List;

public interface NetworkScanCallback {
    void onProgress(int current, int total);
    void onComplete(List<Device> devices);
    void onError(String message);
}
package com.boxuploadperf.http;

import java.util.concurrent.ConcurrentHashMap;

public final class ConnectionReuseTracker {

    private final ConcurrentHashMap<String, Boolean> hostConnected = new ConcurrentHashMap<>();

    public boolean markIfReused(String host) {
        return hostConnected.put(host, Boolean.TRUE) != null;
    }

    public void reset() {
        hostConnected.clear();
    }
}

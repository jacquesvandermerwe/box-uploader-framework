package com.boxuploadperf.http;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ConnectionReuseTracker {

    private final ConcurrentHashMap<String, AtomicBoolean> hostConnected = new ConcurrentHashMap<>();

    public boolean markIfReused(String host) {
        AtomicBoolean flag = hostConnected.computeIfAbsent(host, h -> new AtomicBoolean(false));
        return !flag.compareAndSet(false, true);
    }

    public void reset() {
        hostConnected.clear();
    }
}

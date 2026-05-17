package com.boxuploadperf.http;

public final class NetworkTimingContext {

    private static final ThreadLocal<NetworkTiming> CURRENT = new ThreadLocal<>();

    private NetworkTimingContext() {}

    public static void begin(NetworkTiming timing) {
        CURRENT.set(timing);
    }

    public static NetworkTiming current() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}

package com.boxuploadperf.box;

/** Per-upload-thread HTTP attempt stats (cleared after each {@link BoxClient#uploadFile} call). */
final class UploadAttemptTracker {

    private static final ThreadLocal<State> CURRENT = new ThreadLocal<>();

    private UploadAttemptTracker() {}

    static void begin() {
        CURRENT.set(new State());
    }

    static void clear() {
        CURRENT.remove();
    }

    static State state() {
        State s = CURRENT.get();
        return s != null ? s : new State();
    }

    static void recordHttpAttempt(int statusCode) {
        State s = CURRENT.get();
        if (s == null) {
            return;
        }
        s.httpAttempts++;
        if (statusCode > 0) {
            s.lastStatusCode = statusCode;
        }
        if (statusCode == 429) {
            s.saw429 = true;
        }
    }

    static final class State {
        int lastStatusCode;
        int httpAttempts;
        boolean saw429;
    }
}

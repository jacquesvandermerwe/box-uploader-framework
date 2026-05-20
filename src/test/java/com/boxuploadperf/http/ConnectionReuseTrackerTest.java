package com.boxuploadperf.http;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ConnectionReuseTrackerTest {

    @Test
    void marksReusedCorrectly() {
        ConnectionReuseTracker tracker = new ConnectionReuseTracker();
        assertFalse(tracker.markIfReused("api.box.com"));
        assertTrue(tracker.markIfReused("api.box.com"));
        assertFalse(tracker.markIfReused("upload.box.com"));
        assertTrue(tracker.markIfReused("upload.box.com"));
        assertTrue(tracker.markIfReused("api.box.com"));
    }

    @Test
    void resetsCorrectly() {
        ConnectionReuseTracker tracker = new ConnectionReuseTracker();
        assertFalse(tracker.markIfReused("api.box.com"));
        assertTrue(tracker.markIfReused("api.box.com"));
        tracker.reset();
        assertFalse(tracker.markIfReused("api.box.com"));
    }

    @Test
    void handlesConcurrentAccess() throws Exception {
        ConnectionReuseTracker tracker = new ConnectionReuseTracker();
        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            tasks.add(() -> tracker.markIfReused("shared-host"));
        }

        List<Future<Boolean>> futures = executor.invokeAll(tasks);
        int firstTimes = 0;
        int reuseTimes = 0;
        for (Future<Boolean> f : futures) {
            if (f.get()) {
                reuseTimes++;
            } else {
                firstTimes++;
            }
        }
        executor.shutdown();

        assertEquals(1, firstTimes);
        assertEquals(99, reuseTimes);
    }
}

package com.boxuploadperf.http;

import java.nio.ByteBuffer;
import java.util.concurrent.Flow;
import java.util.function.Supplier;

public final class TimedBodyPublisher implements Flow.Publisher<ByteBuffer> {

    private final Supplier<byte[]> dataSupplier;
    private final NetworkTiming timing;

    public TimedBodyPublisher(byte[] data, NetworkTiming timing) {
        this.dataSupplier = () -> data;
        this.timing = timing;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
        byte[] data = dataSupplier.get();
        timing.requestBytes = data.length;
        long transferStart = System.nanoTime();
        subscriber.onSubscribe(new Flow.Subscription() {
            boolean done;

            @Override
            public void request(long n) {
                if (!done) {
                    done = true;
                    subscriber.onNext(ByteBuffer.wrap(data));
                    timing.transferMs = (System.nanoTime() - transferStart) / 1_000_000.0;
                    timing.durationMs = timing.dnsLookupMs + timing.tcpConnectMs + timing.tlsHandshakeMs
                            + timing.timeToFirstByteMs + timing.transferMs;
                    subscriber.onComplete();
                }
            }

            @Override
            public void cancel() {
                done = true;
            }
        });
    }
}

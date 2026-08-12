package ru.privatenull.pnrelog.util;

/** Epoch-like milliseconds backed by nanoTime so wall-clock corrections cannot extend combat. */
public final class MonotonicClock implements TimeSource {
    private final long baseMillis = System.currentTimeMillis();
    private final long baseNanos = System.nanoTime();

    @Override
    public long now() {
        return baseMillis + (System.nanoTime() - baseNanos) / 1_000_000L;
    }
}

package ru.privatenull.pnrelog.scheduler;

@FunctionalInterface
public interface ScheduledHandle {
    ScheduledHandle NOOP = () -> { };

    void cancel();
}

package ru.privatenull.pnrelog.util;

@FunctionalInterface
public interface TimeSource {
    long now();
}

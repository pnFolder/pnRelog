package ru.privatenull.pnrelog.audit;

import ru.privatenull.pnrelog.config.PluginSettings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Local-only administrator history. No webhook or remote transmission is used. */
public final class AuditService implements AutoCloseable {
    private final Logger logger;
    private final Path directory;
    private final ExecutorService writer;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final Deque<AuditRecord> recent = new ArrayDeque<>();
    private volatile PluginSettings.Audit settings;

    public AuditService(Logger logger, Path directory, PluginSettings.Audit settings) {
        this.logger = logger;
        this.directory = directory;
        this.settings = settings;
        this.writer = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "pnRelog-audit");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void update(PluginSettings.Audit settings) { this.settings = settings; }

    public void record(String type, UUID playerId, String playerName, String detail) {
        PluginSettings.Audit current = settings;
        if (!current.enabled()) return;
        AuditRecord record = new AuditRecord(Instant.now(), type, playerId,
                playerName == null ? "" : playerName, detail == null ? "" : detail);
        synchronized (recent) {
            recent.addLast(record);
            while (recent.size() > current.recentRecords()) recent.removeFirst();
        }
        writer.execute(() -> {
            append(record, current);
            if (current.webhookEnabled() && current.webhookUrl() != null
                    && isImportant(type)) sendWebhook(record, current.webhookUrl());
        });
    }

    public List<AuditRecord> history(UUID playerId, int limit) {
        List<AuditRecord> result = new ArrayList<>();
        synchronized (recent) {
            var iterator = recent.descendingIterator();
            while (iterator.hasNext() && result.size() < limit) {
                AuditRecord record = iterator.next();
                if (playerId.equals(record.playerId())) result.add(record);
            }
        }
        return List.copyOf(result);
    }

    private void append(AuditRecord record, PluginSettings.Audit current) {
        try {
            Files.createDirectories(directory);
            Path file = directory.resolve(current.file());
            if (Files.exists(file) && Files.size(file) >= current.maxFileBytes()) {
                Files.deleteIfExists(directory.resolve(current.file() + ".1"));
                Files.move(file, directory.resolve(current.file() + ".1"), StandardCopyOption.REPLACE_EXISTING);
            }
            Files.writeString(file, toJson(record) + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException exception) {
            logger.log(Level.WARNING, "Не удалось записать локальный audit pnRelog", exception);
        }
    }

    private static String toJson(AuditRecord record) {
        return "{\"at\":\"" + escape(record.at().toString())
                + "\",\"type\":\"" + escape(record.type())
                + "\",\"player_id\":\"" + (record.playerId() == null ? "" : record.playerId())
                + "\",\"player_name\":\"" + escape(record.playerName())
                + "\",\"detail\":\"" + escape(record.detail()) + "\"}";
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n");
    }

    private void sendWebhook(AuditRecord record, URI url) {
        String content = "[pnRelog] " + record.type() + " | " + record.playerName()
                + " | " + record.detail();
        String body = "{\"content\":\"" + escape(content) + "\"}";
        HttpRequest request = HttpRequest.newBuilder(url)
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                logger.warning("Audit webhook pnRelog вернул HTTP " + response.statusCode());
            }
        } catch (IOException exception) {
            logger.warning("Audit webhook pnRelog недоступен: " + exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static boolean isImportant(String type) {
        return type.equals("ESCAPE_PUNISHED") || type.equals("ESCAPE_EXEMPT")
                || type.equals("ESCAPE_SUPPRESSED") || type.equals("CIRCUIT_OPENED");
    }

    @Override public void close() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(5, TimeUnit.SECONDS)) writer.shutdownNow();
        } catch (InterruptedException exception) {
            writer.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

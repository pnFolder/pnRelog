package ru.privatenull.pnrelog.update;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.privatenull.pnrelog.PnRelogPlugin;
import ru.privatenull.pnrelog.config.PluginSettings;
import ru.privatenull.pnrelog.scheduler.PluginScheduler;
import ru.privatenull.pnrelog.text.MessageService;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UpdateService {
    private static final String REPOSITORY = "pnFolder/pnRelog";
    public record Release(String version, URI page, URI asset) {
    }

    private static final Pattern TAG = Pattern.compile("\\\"tag_name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern PAGE = Pattern.compile("\\\"html_url\\\"\\s*:\\s*\\\"([^\\\"]+/releases/tag/[^\\\"]+)\\\"");
    private static final Pattern ASSET = Pattern.compile("\\\"browser_download_url\\\"\\s*:\\s*\\\"([^\\\"]+\\.jar)\\\"");
    private static final int MAX_JAR_BYTES = 64 * 1024 * 1024;

    private final PnRelogPlugin plugin;
    private final PluginScheduler scheduler;
    private final MessageService messages;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8)).build();
    private volatile PluginSettings.Updates settings;
    private volatile Release latest;

    public UpdateService(PnRelogPlugin plugin, PluginScheduler scheduler,
                         MessageService messages, PluginSettings.Updates settings) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.messages = messages;
        this.settings = settings;
    }

    public void updateSettings(PluginSettings.Updates settings) {
        this.settings = settings;
    }

    public CompletableFuture<Optional<Release>> check() {
        PluginSettings.Updates current = settings;
        if (!current.enabled()) return CompletableFuture.completedFuture(Optional.empty());
        URI uri = URI.create("https://api.github.com/repos/" + REPOSITORY + "/releases/latest");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(12))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "pnRelog/" + plugin.getDescription().getVersion())
                .GET().build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new IllegalStateException("GitHub HTTP " + response.statusCode());
                    }
                    Release release = parse(response.body());
                    if (compareVersions(release.version(), plugin.getDescription().getVersion()) > 0) {
                        latest = release;
                        return Optional.of(release);
                    }
                    latest = null;
                    return Optional.<Release>empty();
                });
    }

    public void checkAndNotify(CommandSender sender) {
        check().whenComplete((release, error) -> {
            if (error != null) {
                send(sender, "update-error", Map.of("error", rootMessage(error)));
            } else if (release.isPresent()) {
                send(sender, "update-found", Map.of("version", release.get().version(),
                        "url", release.get().page().toString()));
            } else send(sender, "update-none", Map.of("version", plugin.getDescription().getVersion()));
        });
    }

    public void install(CommandSender sender) {
        if (!settings.downloadEnabled()) {
            send(sender, "update-error", Map.of("error", "скачивание отключено в config.yml"));
            return;
        }
        CompletableFuture<Optional<Release>> source = latest == null ? check()
                : CompletableFuture.completedFuture(Optional.of(latest));
        source.thenCompose(optional -> {
            if (optional.isEmpty()) return CompletableFuture.failedFuture(
                    new IllegalStateException("новая версия не найдена"));
            Release release = optional.get();
            if (release.asset() == null) return CompletableFuture.failedFuture(
                    new IllegalStateException("JAR asset отсутствует в релизе"));
            HttpRequest request = HttpRequest.newBuilder(release.asset())
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "pnRelog/" + plugin.getDescription().getVersion())
                    .GET().build();
            return client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                    .thenApply(response -> writeUpdate(release, response));
        }).whenComplete((path, error) -> {
            if (error != null) send(sender, "update-error", Map.of("error", rootMessage(error)));
            else send(sender, "update-staged", Map.of("file", path.toString()));
        });
    }

    public void startupCheck() {
        check().whenComplete((release, error) -> {
            if (error != null) {
                plugin.getLogger().warning("Проверка обновлений: " + rootMessage(error));
            } else release.ifPresent(value -> plugin.getLogger().warning(
                    "Доступен pnRelog " + value.version() + ": " + value.page()));
        });
    }

    private Path writeUpdate(Release release, HttpResponse<byte[]> response) {
        if (response.statusCode() != 200) throw new IllegalStateException("Download HTTP " + response.statusCode());
        byte[] bytes = response.body();
        if (bytes.length < 4 || bytes.length > MAX_JAR_BYTES
                || bytes[0] != 'P' || bytes[1] != 'K') {
            throw new IllegalStateException("загруженный файл не является допустимым JAR");
        }
        try {
            Path updateDirectory = plugin.getDataFolder().toPath().getParent().resolve("update");
            Files.createDirectories(updateDirectory);
            Path target = updateDirectory.resolve("pnRelog-" + safeVersion(release.version()) + ".jar");
            Files.write(target, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return target;
        } catch (IOException exception) {
            throw new IllegalStateException("не удалось записать update JAR", exception);
        }
    }

    private void send(CommandSender sender, String key, Map<String, ?> placeholders) {
        Runnable task = () -> messages.send(sender, key, placeholders);
        if (sender instanceof Player player) scheduler.runEntity(player, task);
        else scheduler.runGlobal(task);
    }

    static Release parse(String json) {
        String tag = find(TAG, json).orElseThrow(() -> new IllegalStateException("tag_name отсутствует"));
        URI page = URI.create(find(PAGE, json)
                .orElse("https://github.com/" + REPOSITORY + "/releases/tag/" + tag));
        URI asset = find(ASSET, json).map(URI::create).orElse(null);
        return new Release(normalizeVersion(tag), page, asset);
    }

    static int compareVersions(String left, String right) {
        String[] first = normalizeVersion(left).split("[.-]");
        String[] second = normalizeVersion(right).split("[.-]");
        int length = Math.max(first.length, second.length);
        for (int index = 0; index < length; index++) {
            String a = index < first.length ? first[index] : "0";
            String b = index < second.length ? second[index] : "0";
            int comparison;
            try {
                comparison = Integer.compare(Integer.parseInt(a), Integer.parseInt(b));
            } catch (NumberFormatException ignored) {
                comparison = a.compareToIgnoreCase(b);
            }
            if (comparison != 0) return comparison;
        }
        return 0;
    }

    private static Optional<String> find(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(value);
        return matcher.find() ? Optional.of(matcher.group(1).replace("\\/", "/")) : Optional.empty();
    }

    private static String normalizeVersion(String value) {
        String normalized = value.strip();
        return normalized.startsWith("v") || normalized.startsWith("V") ? normalized.substring(1) : normalized;
    }

    private static String safeVersion(String value) {
        return normalizeVersion(value).replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}

package ru.privatenull.pnrelog.text;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class MessageService {
    private YamlConfiguration configuration;

    public MessageService(YamlConfiguration configuration) {
        this.configuration = configuration;
    }

    public void replace(YamlConfiguration configuration) {
        this.configuration = configuration;
    }

    public String get(String path) {
        String value = configuration.getString(path, "&cMissing message: " + path);
        return render(value, Map.of());
    }

    public String get(String path, Map<String, ?> placeholders) {
        String value = configuration.getString(path, "&cMissing message: " + path);
        return render(value, placeholders);
    }

    public List<String> list(String path) {
        List<String> output = new ArrayList<>();
        for (String line : configuration.getStringList(path)) output.add(render(line, Map.of()));
        return output;
    }

    public void send(CommandSender sender, String path) {
        String message = get(path);
        if (sender instanceof org.bukkit.entity.Player player) message = PlaceholderSupport.parse(player, message);
        sender.sendMessage(message);
    }

    public void send(CommandSender sender, String path, Map<String, ?> placeholders) {
        String message = get(path, placeholders);
        if (sender instanceof org.bukkit.entity.Player player) message = PlaceholderSupport.parse(player, message);
        sender.sendMessage(message);
    }

    public String render(String template, Map<String, ?> placeholders) {
        String result = template.replace("{prefix}", configuration.getString("prefix", ""));
        for (Map.Entry<String, ?> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return Colorizer.color(result);
    }
}

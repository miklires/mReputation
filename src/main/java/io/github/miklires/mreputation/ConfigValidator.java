package io.github.miklires.mreputation;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

final class ConfigValidator {
    private final MReputation plugin;
    ConfigValidator(MReputation plugin) { this.plugin = plugin; }

    void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();
        config.options().copyDefaults(true);
        config.set("config-version", 2);
        String language = config.getString("language", "en_US");
        if (!"en_US".equalsIgnoreCase(language) && !"ru_RU".equalsIgnoreCase(language)) config.set("language", "en_US");
        int minimum = bounded(config, "reputation.minimum", -1_000_000, 1_000_000, 0);
        int maximum = bounded(config, "reputation.maximum", -1_000_000, 1_000_000, 1000);
        if (minimum > maximum) { minimum = 0; maximum = 1000; config.set("reputation.minimum", minimum); config.set("reputation.maximum", maximum); }
        bounded(config, "reputation.default", minimum, maximum, Math.clamp(500, minimum, maximum));
        bounded(config, "reputation.positive-threshold", minimum, maximum, Math.clamp(500, minimum, maximum));
        bounded(config, "reputation.points-above-threshold-per-action", 1, 1_000_000, 1);
        bounded(config, "reputation.maximum-adjustment-per-command", 1, 1_000_000, 1000);
        bounded(config, "reputation.minimum-reason-length", 1, 160, 3);
        bounded(config, "reputation.history-limit", 1, 500, 50);
        bounded(config, "display.leaderboard-page-size", 5, 20, 10);
        String zeroCommand = config.getString("zero-action.command", "");
        if (config.getBoolean("zero-action.enabled") && !safeCommand(zeroCommand)) {
            config.set("zero-action.enabled", false);
            plugin.getLogger().warning("zero-action was disabled because its command is empty or unsafe");
        }
        int metrics = config.getInt("metrics.bstats-id", 33360);
        if (metrics < 0) config.set("metrics.bstats-id", 33360);
        String project = config.getString("updates.modrinth-project-id", "mReputation");
        if (project == null || !project.matches("[A-Za-z0-9_-]{3,64}")) config.set("updates.modrinth-project-id", "mReputation");
        if (config.getMapList("tiers").isEmpty()) config.set("tiers", defaultTiers());
        plugin.saveConfig();
    }

    private static int bounded(FileConfiguration config, String path, int minimum, int maximum, int fallback) {
        int value = config.getInt(path, fallback);
        if (value < minimum || value > maximum) { config.set(path, fallback); return fallback; }
        return value;
    }

    private static boolean safeCommand(String command) {
        return command != null && !command.isBlank() && command.length() <= 512 && command.chars().noneMatch(Character::isISOControl);
    }

    private static List<?> defaultTiers() {
        return List.of(
                java.util.Map.of("id", "critical", "minimum", 0, "display", "<dark_red>Critical", "enter-commands", List.of()),
                java.util.Map.of("id", "neutral", "minimum", 500, "display", "<gray>Neutral", "enter-commands", List.of()),
                java.util.Map.of("id", "trusted", "minimum", 750, "display", "<green>Trusted", "enter-commands", List.of()));
    }
}

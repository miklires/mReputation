package io.github.miklires.mreputation;

import io.github.miklires.mreputation.api.ReputationAPI;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.UUID;

public final class MReputation extends JavaPlugin {
    private ReputationService service;
    private MessageBundle messages;
    private ConfigValidator configValidator;

    @Override public void onEnable() {
        configValidator = new ConfigValidator(this);
        configValidator.load();
        saveLang("en_US");
        saveLang("ru_RU");
        messages = new MessageBundle(this);
        service = new ReputationService(this);
        getServer().getServicesManager().register(ReputationAPI.class, service, this, ServicePriority.Normal);
        ReputationCommand command = new ReputationCommand(this);
        if (getCommand("reputation") != null) {
            getCommand("reputation").setExecutor(command);
            getCommand("reputation").setTabCompleter(command);
        }
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) new PlaceholderHook(this).register();
        int metricsId = Math.max(0, getConfig().getInt("metrics.bstats-id", 0));
        if (getConfig().getBoolean("metrics.enabled", true) && metricsId > 0) new Metrics(this, metricsId);
        if (getConfig().getBoolean("updates.enabled", true)) UpdateChecker.checkAsync(this,
                getConfig().getString("updates.modrinth-project-id", ""));
        getLogger().info("mReputation " + getPluginMeta().getVersion() + " enabled");
    }

    @Override public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
        if (service != null) service.close();
    }

    void reloadAll() {
        configValidator.load();
        messages.reload();
        service.reloadPolicy();
    }

    void handleMinimumCrossing(UUID id, String name, int oldValue, int newValue, int minimum) {
        if (oldValue <= minimum || newValue > minimum || !getConfig().getBoolean("zero-action.enabled", false)) return;
        var online = Bukkit.getPlayer(id);
        if (online != null && online.hasPermission("mreputation.exempt.zero")) return;
        String command = getConfig().getString("zero-action.command", "");
        dispatchConfiguredCommand(command, id, name, newValue);
    }

    void handleTierChange(UUID id, String name, int value, ReputationService.TierRule oldTier, ReputationService.TierRule newTier) {
        for (String command : newTier.commands()) dispatchConfiguredCommand(command, id, name, value);
    }

    private void dispatchConfiguredCommand(String template, UUID id, String name, int value) {
        if (template == null || template.isBlank() || !name.matches("[A-Za-z0-9_]{1,16}")) return;
        String command = template.replace("{player}", name).replace("{uuid}", id.toString()).replace("{value}", Integer.toString(value));
        if (command.length() > 512 || command.chars().anyMatch(Character::isISOControl)) return;
        getServer().getGlobalRegionScheduler().execute(this,
                () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.startsWith("/") ? command.substring(1) : command));
    }

    private void saveLang(String locale) {
        String path = "lang/" + locale + ".yml";
        if (!new File(getDataFolder(), path).exists()) saveResource(path, false);
    }

    ReputationService service() { return service; }
    MessageBundle messages() { return messages; }
}

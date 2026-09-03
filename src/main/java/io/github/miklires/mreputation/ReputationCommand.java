package io.github.miklires.mreputation;

import io.github.miklires.mreputation.api.ReputationStanding;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

final class ReputationCommand implements CommandExecutor, TabCompleter {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm");
    private final MReputation plugin;
    ReputationCommand(MReputation plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        String sub = args.length == 0 ? "show" : args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "show" -> show(sender, args);
            case "top" -> leaderboard(sender, args, false);
            case "bottom" -> leaderboard(sender, args, true);
            case "tiers" -> tiers(sender);
            case "history" -> history(sender, args);
            case "add", "remove", "set" -> modify(sender, args, sub);
            case "reset" -> reset(sender, args);
            case "reload" -> reload(sender);
            default -> { send(sender, "usage"); yield true; }
        };
    }

    private boolean show(CommandSender sender, String[] args) {
        boolean other = args.length > 1;
        if (!allowed(sender, other ? "mreputation.command.show.other" : "mreputation.command.show")) return true;
        Target target = resolve(sender, other ? args[1] : null);
        if (target == null) { send(sender, "player-not-found"); return true; }
        int value = plugin.service().get(target.id());
        var tier = plugin.service().tier(target.id());
        sendTrusted(sender, "show", "tier", tier.displayName(), "player", target.name(), "value", Integer.toString(value));
        return true;
    }

    private boolean leaderboard(CommandSender sender, String[] args, boolean lowestFirst) {
        if (!allowed(sender, "mreputation.command.leaderboard")) return true;
        int page = positivePage(args.length > 1 ? args[1] : "1");
        if (page < 1) { send(sender, "invalid-number"); return true; }
        int pageSize = Math.clamp(plugin.getConfig().getInt("display.leaderboard-page-size", 10), 5, 20);
        int requested = Math.min(100, page * pageSize);
        List<ReputationStanding> entries = plugin.service().leaderboard(requested, lowestFirst);
        int from = (page - 1) * pageSize;
        if (from >= entries.size()) { send(sender, "leaderboard-empty"); return true; }
        send(sender, lowestFirst ? "bottom-title" : "top-title", "page", Integer.toString(page));
        for (int index = from; index < Math.min(entries.size(), from + pageSize); index++) {
            ReputationStanding entry = entries.get(index);
            send(sender, "leaderboard-line", "position", Integer.toString(index + 1), "player", entry.playerName(),
                    "value", Integer.toString(entry.value()), "tier", entry.tier().id());
        }
        return true;
    }

    private boolean tiers(CommandSender sender) {
        if (!allowed(sender, "mreputation.command.tiers")) return true;
        send(sender, "tiers-title");
        plugin.service().tiers().forEach(tier -> sendTrusted(sender, "tier-line", "tier", tier.displayName(),
                "minimum", Integer.toString(tier.minimum())));
        return true;
    }

    private boolean history(CommandSender sender, String[] args) {
        boolean other = args.length > 1;
        if (!allowed(sender, other ? "mreputation.command.history.other" : "mreputation.command.history.self")) return true;
        Target target = resolve(sender, other ? args[1] : null);
        if (target == null) { send(sender, "player-not-found"); return true; }
        int page = positivePage(args.length > 2 ? args[2] : "1");
        if (page < 1) { send(sender, "invalid-number"); return true; }
        var history = plugin.service().history(target.id());
        int pageSize = 10;
        int from = (page - 1) * pageSize;
        if (history.isEmpty() || from >= history.size()) { send(sender, "history-empty", "player", target.name()); return true; }
        send(sender, "history-title", "player", target.name(), "page", Integer.toString(page));
        List<ReputationEntry> newest = new ArrayList<>(history); java.util.Collections.reverse(newest);
        for (int index = from; index < Math.min(newest.size(), from + pageSize); index++) {
            ReputationEntry entry = newest.get(index);
            send(sender, "history-line", "time", TIME.format(Instant.ofEpochMilli(entry.timestamp()).atZone(ZoneId.systemDefault())),
                    "delta", signed(entry.delta()), "actor", entry.actor(), "reason", entry.reason());
        }
        return true;
    }

    private boolean modify(CommandSender sender, String[] args, String sub) {
        if (!allowed(sender, "mreputation.command.modify")) return true;
        if (args.length < 4) { send(sender, "usage"); return true; }
        Target target = resolve(sender, args[1]);
        if (target == null) { send(sender, "player-not-found"); return true; }
        int amount;
        try { amount = Integer.parseInt(args[2]); } catch (NumberFormatException exception) { send(sender, "invalid-number"); return true; }
        int maximumAdjustment = Math.clamp(plugin.getConfig().getInt("reputation.maximum-adjustment-per-command", 1000), 1, 1_000_000);
        if (!sub.equals("set") && (amount <= 0 || amount > maximumAdjustment)) { send(sender, "invalid-adjustment", "maximum", Integer.toString(maximumAdjustment)); return true; }
        String reason = reason(args, 3);
        if (!validReason(reason)) { send(sender, "reason-required"); return true; }
        int old = plugin.service().get(target.id());
        boolean changed = switch (sub) {
            case "set" -> plugin.service().set(target.id(), target.name(), amount, sender.getName(), reason);
            case "remove" -> plugin.service().change(target.id(), target.name(), -amount, sender.getName(), reason);
            default -> plugin.service().change(target.id(), target.name(), amount, sender.getName(), reason);
        };
        changed(sender, target, old, changed);
        return true;
    }

    private boolean reset(CommandSender sender, String[] args) {
        if (!allowed(sender, "mreputation.command.modify")) return true;
        if (args.length < 3) { send(sender, "usage"); return true; }
        Target target = resolve(sender, args[1]);
        if (target == null) { send(sender, "player-not-found"); return true; }
        String reason = reason(args, 2);
        if (!validReason(reason)) { send(sender, "reason-required"); return true; }
        int old = plugin.service().get(target.id());
        changed(sender, target, old, plugin.service().reset(target.id(), target.name(), sender.getName(), reason));
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!allowed(sender, "mreputation.command.reload")) return true;
        plugin.reloadAll();
        send(sender, "reloaded");
        return true;
    }

    private void changed(CommandSender sender, Target target, int old, boolean changed) {
        int now = plugin.service().get(target.id());
        if (!changed) { send(sender, "unchanged", "player", target.name()); return; }
        send(sender, "changed", "player", target.name(), "old", Integer.toString(old), "new", Integer.toString(now), "delta", signed(now - old));
    }

    private Target resolve(CommandSender sender, String raw) {
        if (raw == null) return sender instanceof Player player ? new Target(player.getUniqueId(), player.getName()) : null;
        try {
            UUID id = UUID.fromString(raw);
            OfflinePlayer offline = Bukkit.getOfflinePlayer(id);
            return new Target(id, offline.getName() == null ? raw : offline.getName());
        } catch (IllegalArgumentException ignored) { }
        Player online = Bukkit.getPlayerExact(raw);
        if (online != null) return new Target(online.getUniqueId(), online.getName());
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(raw);
        return cached == null ? null : new Target(cached.getUniqueId(), cached.getName() == null ? raw : cached.getName());
    }

    private boolean validReason(String reason) {
        int minimum = Math.clamp(plugin.getConfig().getInt("reputation.minimum-reason-length", 3), 1, 160);
        return reason.length() >= minimum && reason.length() <= 160 && reason.chars().noneMatch(Character::isISOControl);
    }
    private boolean allowed(CommandSender sender, String permission) { if (sender.hasPermission(permission)) return true; send(sender, "no-permission"); return false; }
    private void send(CommandSender sender, String key, String... values) { sender.sendMessage(MM.deserialize(plugin.messages().text(key, values))); }
    private void sendTrusted(CommandSender sender, String key, String trustedKey, String trustedValue, String... values) { sender.sendMessage(MM.deserialize(plugin.messages().textTrusted(key, trustedKey, trustedValue, values))); }
    private static String reason(String[] args, int start) { String value = String.join(" ", Arrays.copyOfRange(args, start, args.length)).strip(); return value.length() <= 160 ? value : value.substring(0, 160); }
    private static int positivePage(String raw) { try { return Integer.parseInt(raw); } catch (NumberFormatException exception) { return -1; } }
    private static String signed(int value) { return value > 0 ? "+" + value : Integer.toString(value); }
    private record Target(UUID id, String name) { }

    @Override public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (args.length == 1) return List.of("show", "top", "bottom", "tiers", "history", "add", "remove", "set", "reset", "reload").stream()
                .filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 2 && Set.of("show", "history", "add", "remove", "set", "reset").contains(args[0].toLowerCase(Locale.ROOT)))
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(value -> value.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        return List.of();
    }
}

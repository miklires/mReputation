package io.github.miklires.mreputation;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

final class PlaceholderHook extends PlaceholderExpansion {
    private final MReputation plugin;
    PlaceholderHook(MReputation plugin) { this.plugin = plugin; }
    @Override public @NotNull String getIdentifier() { return "mreputation"; }
    @Override public @NotNull String getAuthor() { return "miklires"; }
    @Override public @NotNull String getVersion() { return plugin.getPluginMeta().getVersion(); }
    @Override public boolean persist() { return true; }
    @Override public String onRequest(OfflinePlayer player, @NotNull String parameters) {
        if (player == null) return "";
        return switch (parameters.toLowerCase(java.util.Locale.ROOT)) {
            case "value" -> Integer.toString(plugin.service().get(player.getUniqueId()));
            case "tier", "tier_id" -> plugin.service().tier(player.getUniqueId()).id();
            case "tier_display" -> PlainTextComponentSerializer.plainText().serialize(
                    MiniMessage.miniMessage().deserialize(plugin.service().tier(player.getUniqueId()).displayName()));
            default -> null;
        };
    }
}

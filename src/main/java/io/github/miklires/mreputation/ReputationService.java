package io.github.miklires.mreputation;

import io.github.miklires.mreputation.api.ReputationAPI;
import io.github.miklires.mreputation.api.ReputationChangeEvent;
import io.github.miklires.mreputation.api.ReputationStanding;
import io.github.miklires.mreputation.api.ReputationTier;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ReputationService implements ReputationAPI {
    private static final int MAX_HISTORY = 500;
    private final MReputation plugin;
    private final ReputationStorage storage;
    private final Map<UUID, ReputationStorage.Stored> records = new ConcurrentHashMap<>();
    private volatile ReputationPolicy policy;
    private volatile int historyLimit;
    private volatile List<TierRule> tiers;

    ReputationService(MReputation plugin) {
        this.plugin = plugin;
        this.storage = new ReputationStorage(plugin);
        records.putAll(storage.load());
        reloadPolicy();
    }

    void reloadPolicy() {
        var config = plugin.getConfig();
        ReputationPolicy loadedPolicy = new ReputationPolicy(config.getInt("reputation.default", 500),
                config.getInt("reputation.minimum", 0), config.getInt("reputation.maximum", 1000),
                config.getInt("reputation.positive-threshold", 500),
                config.getInt("reputation.points-above-threshold-per-action", 1));
        int loadedHistoryLimit = Math.clamp(config.getInt("reputation.history-limit", 50), 1, MAX_HISTORY);
        List<TierRule> loadedTiers = loadTiers(config.getMapList("tiers"), loadedPolicy.minimum());
        policy = loadedPolicy;
        historyLimit = loadedHistoryLimit;
        tiers = loadedTiers;
        normalizeLoadedRecords();
    }

    @Override public int get(UUID id) {
        ReputationStorage.Stored record = records.get(id);
        return record == null ? policy.defaultValue() : record.value();
    }

    @Override public ReputationTier tier(UUID id) { return tierFor(get(id)).view(); }

    @Override public List<ReputationEntry> history(UUID id) {
        ReputationStorage.Stored record = records.get(id);
        return record == null ? List.of() : List.copyOf(record.history());
    }

    @Override public List<ReputationStanding> leaderboard(int limit, boolean lowestFirst) {
        int boundedLimit = Math.clamp(limit, 1, 100);
        Comparator<ReputationStorage.Stored> comparator = Comparator.comparingInt(ReputationStorage.Stored::value)
                .thenComparing(ReputationStorage.Stored::name, String.CASE_INSENSITIVE_ORDER);
        if (!lowestFirst) comparator = comparator.reversed();
        Comparator<ReputationStorage.Stored> finalComparator = comparator;
        return records.entrySet().stream().sorted(Map.Entry.comparingByValue(finalComparator)).limit(boundedLimit)
                .map(entry -> new ReputationStanding(entry.getKey(), entry.getValue().name(), entry.getValue().value(),
                        tierFor(entry.getValue().value()).view())).toList();
    }

    @Override public boolean change(UUID id, String name, int delta, String actor, String reason) {
        int old = get(id);
        return apply(id, safeName(id, name), old, policy.change(old, delta), safeActor(actor), safeReason(reason));
    }

    @Override public boolean set(UUID id, String name, int value, String actor, String reason) {
        int old = get(id);
        return apply(id, safeName(id, name), old, policy.set(value), safeActor(actor), safeReason(reason));
    }

    @Override public boolean reset(UUID id, String name, String actor, String reason) {
        return set(id, name, policy.defaultValue(), actor, reason);
    }

    private synchronized boolean apply(UUID id, String name, int oldValue, int newValue, String actor, String reason) {
        if (id == null || oldValue == newValue) return false;
        ReputationChangeEvent event = new ReputationChangeEvent(id, oldValue, newValue, actor, reason);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;
        List<ReputationEntry> history = new ArrayList<>(history(id));
        history.add(new ReputationEntry(System.currentTimeMillis(), newValue - oldValue, newValue, actor, reason));
        while (history.size() > historyLimit) history.removeFirst();
        records.put(id, new ReputationStorage.Stored(newValue, name, history));
        storage.saveAsync(Map.copyOf(records));
        TierRule oldTier = tierFor(oldValue);
        TierRule newTier = tierFor(newValue);
        if (!oldTier.view().id().equals(newTier.view().id())) plugin.handleTierChange(id, name, newValue, oldTier, newTier);
        plugin.handleMinimumCrossing(id, name, oldValue, newValue, policy.minimum());
        return true;
    }

    private void normalizeLoadedRecords() {
        boolean changed = false;
        for (Map.Entry<UUID, ReputationStorage.Stored> entry : new ArrayList<>(records.entrySet())) {
            ReputationStorage.Stored stored = entry.getValue();
            int value = policy.set(stored.value());
            List<ReputationEntry> history = stored.history();
            if (history.size() > historyLimit) history = List.copyOf(history.subList(history.size() - historyLimit, history.size()));
            String name = safeName(entry.getKey(), stored.name());
            ReputationStorage.Stored normalized = new ReputationStorage.Stored(value, name, history);
            if (!normalized.equals(stored)) { records.put(entry.getKey(), normalized); changed = true; }
        }
        if (changed) storage.saveAsync(Map.copyOf(records));
    }

    private TierRule tierFor(int value) {
        TierRule result = tiers.getFirst();
        for (TierRule tier : tiers) {
            if (value < tier.view().minimum()) break;
            result = tier;
        }
        return result;
    }

    private static List<TierRule> loadTiers(List<Map<?, ?>> configured, int minimum) {
        Map<String, TierRule> unique = new LinkedHashMap<>();
        for (Map<?, ?> raw : configured) {
            Object rawId = raw.containsKey("id") ? raw.get("id") : "";
            String id = String.valueOf(rawId).strip().toLowerCase(Locale.ROOT);
            if (!id.matches("[a-z0-9_-]{1,32}")) continue;
            int threshold;
            Object rawMinimum = raw.containsKey("minimum") ? raw.get("minimum") : minimum;
            try { threshold = Integer.parseInt(String.valueOf(rawMinimum)); }
            catch (NumberFormatException exception) { continue; }
            Object rawDisplay = raw.containsKey("display") ? raw.get("display") : id;
            String display = bounded(String.valueOf(rawDisplay), 80);
            List<String> commands = raw.get("enter-commands") instanceof List<?> list
                    ? list.stream().map(String::valueOf).map(value -> bounded(value, 256)).filter(value -> !value.isBlank()).limit(20).toList()
                    : List.of();
            unique.put(id, new TierRule(new ReputationTier(id, display, threshold), commands));
        }
        if (unique.isEmpty()) unique.put("default", new TierRule(new ReputationTier("default", "<gray>Default", minimum), List.of()));
        List<TierRule> sorted = new ArrayList<>(unique.values());
        sorted.sort(Comparator.comparingInt(rule -> rule.view().minimum()));
        if (sorted.getFirst().view().minimum() > minimum)
            sorted.addFirst(new TierRule(new ReputationTier("minimum", "<dark_red>Minimum", minimum), List.of()));
        return List.copyOf(sorted);
    }

    private static String safeName(UUID id, String value) {
        String name = bounded(value, 36);
        return name.matches("[A-Za-z0-9_]{1,16}") ? name : id.toString();
    }
    private static String safeActor(String value) { String actor = bounded(value, 64); return actor.isBlank() ? "unknown" : actor; }
    private static String safeReason(String value) { String reason = bounded(value, 160); return reason.isBlank() ? "No reason supplied" : reason; }
    private static String bounded(String value, int maximum) {
        String clean = value == null ? "" : value.replaceAll("[\\p{Cntrl}&&[^\\t]]", "").strip();
        return clean.length() <= maximum ? clean : clean.substring(0, maximum);
    }

    List<ReputationTier> tiers() { return tiers.stream().map(TierRule::view).toList(); }
    int defaultValue() { return policy.defaultValue(); }
    void close() { storage.close(); }
    record TierRule(ReputationTier view, List<String> commands) { TierRule { commands = List.copyOf(commands); } }
}

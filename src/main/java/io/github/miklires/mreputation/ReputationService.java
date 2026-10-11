package io.github.miklires.mreputation;

import io.github.miklires.mreputation.api.ReputationAPI;
import io.github.miklires.mreputation.api.ReputationChangeEvent;
import io.github.miklires.mreputation.api.ReputationStanding;
import io.github.miklires.mreputation.api.ReputationTier;
import io.github.miklires.mreputation.api.ReputationVoteResult;
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
    private final Map<ReputationStorage.VoteKey, ReputationStorage.StoredVote> votes = new ConcurrentHashMap<>();
    private volatile ReputationPolicy policy;
    private volatile int historyLimit;
    private volatile List<TierRule> tiers;

    ReputationService(MReputation plugin) {
        this.plugin = plugin;
        this.storage = new ReputationStorage(plugin);
        ReputationStorage.Data loaded = storage.load();
        records.putAll(loaded.records());
        votes.putAll(loaded.votes());
        reloadPolicy();
    }

    void reloadPolicy() {
        var config = plugin.getConfig();
        ReputationPolicy loadedPolicy = new ReputationPolicy(config.getInt("reputation.default", 500),
                config.getInt("reputation.minimum", 0), config.getInt("reputation.maximum", 1000),
                config.getInt("reputation.positive-threshold", 500),
                config.getInt("reputation.points-above-threshold-per-action", 1));
        int loadedHistoryLimit = Math.clamp(config.getInt("reputation.history-limit", 50), 1, MAX_HISTORY);
        List<TierRule> loadedTiers = loadTiers(config.getMapList("tiers"), loadedPolicy.minimum(), loadedPolicy.maximum());
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

    @Override public boolean playerVotingEnabled() {
        return plugin.getConfig().getBoolean("player-voting.enabled", false);
    }

    @Override public boolean negativePlayerVotingEnabled() {
        return playerVotingEnabled() && plugin.getConfig().getBoolean("player-voting.allow-negative", false);
    }

    @Override public synchronized ReputationVoteResult vote(UUID voterId, String voterName, long voterPlaytimeTicks,
                                                            long voterFirstPlayedMillis, UUID targetId, String targetName,
                                                            int direction, String evidence) {
        var config = plugin.getConfig();
        direction = Integer.signum(direction);
        long minimumTicks = Math.clamp(config.getLong("player-voting.minimum-playtime-hours", 2), 0, 10_000)
                * 60L * 60L * 20L;
        long now = System.currentTimeMillis();
        long minimumAgeMillis = Math.clamp(config.getLong("player-voting.minimum-account-age-days", 7), 0, 3650)
                * 86_400_000L;
        int minimumEvidence = Math.clamp(config.getInt("player-voting.minimum-evidence-length", 5), 1, 160);
        String safeEvidence = bounded(evidence, 160);
        ReputationStorage.VoteKey key = new ReputationStorage.VoteKey(voterId, targetId);
        ReputationStorage.StoredVote previous = votes.get(key);
        long cooldownMillis = Math.clamp(config.getLong("player-voting.cooldown-hours", 24), 0, 24L * 365L)
                * 60L * 60L * 1000L;
        int dailyLimit = Math.clamp(config.getInt("player-voting.daily-limit", 10), 1, 1_000);
        long recent = votes.entrySet().stream().filter(entry -> entry.getKey().voterId().equals(voterId))
                .filter(entry -> now - entry.getValue().timestamp() < 86_400_000L).count();
        ReputationVotePolicy rules = new ReputationVotePolicy(config.getBoolean("player-voting.enabled", false),
                config.getBoolean("player-voting.allow-negative", false), minimumTicks, minimumAgeMillis,
                minimumEvidence, cooldownMillis, dailyLimit, config.getBoolean("player-voting.block-reciprocal", true));
        ReputationVoteResult validation = rules.validate(voterId, targetId, voterPlaytimeTicks, voterFirstPlayedMillis,
                direction, safeEvidence, previous == null ? null
                        : new ReputationVotePolicy.Previous(previous.direction(), previous.timestamp()),
                votes.containsKey(new ReputationStorage.VoteKey(targetId, voterId)), recent, now);
        if (validation != ReputationVoteResult.SUCCESS) return validation;

        int points = Math.clamp(config.getInt("player-voting.points-per-vote", 1), 1, 100);
        int oldDirection = previous == null ? 0 : previous.direction();
        int delta = (direction - oldDirection) * points;
        int oldValue = get(targetId);
        boolean changed = apply(targetId, safeName(targetId, targetName), oldValue, policy.set(oldValue + delta),
                "player:" + voterId, (direction > 0 ? "Player endorsement: " : "Player negative vote: ") + safeEvidence,
                false);
        if (!changed) return ReputationVoteResult.REJECTED;
        votes.put(key, new ReputationStorage.StoredVote(direction, now));
        storage.saveAsync(Map.copyOf(records), Map.copyOf(votes));
        return ReputationVoteResult.SUCCESS;
    }

    private synchronized boolean apply(UUID id, String name, int oldValue, int newValue, String actor, String reason) {
        return apply(id, name, oldValue, newValue, actor, reason, true);
    }

    private synchronized boolean apply(UUID id, String name, int oldValue, int newValue, String actor, String reason,
                                       boolean persist) {
        if (id == null || oldValue == newValue) return false;
        ReputationChangeEvent event = new ReputationChangeEvent(id, oldValue, newValue, actor, reason);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;
        List<ReputationEntry> history = new ArrayList<>(history(id));
        history.add(new ReputationEntry(System.currentTimeMillis(), newValue - oldValue, newValue, actor, reason));
        while (history.size() > historyLimit) history.removeFirst();
        records.put(id, new ReputationStorage.Stored(newValue, name, history));
        if (persist) storage.saveAsync(Map.copyOf(records), Map.copyOf(votes));
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
        if (changed) storage.saveAsync(Map.copyOf(records), Map.copyOf(votes));
    }

    private TierRule tierFor(int value) {
        TierRule result = tiers.getFirst();
        for (TierRule tier : tiers) {
            if (value < tier.view().minimum()) break;
            result = tier;
        }
        return result;
    }

    private static List<TierRule> loadTiers(List<Map<?, ?>> configured, int minimum, int maximum) {
        Map<String, TierRule> unique = new LinkedHashMap<>();
        for (Map<?, ?> raw : configured) {
            Object rawId = raw.containsKey("id") ? raw.get("id") : "";
            String id = String.valueOf(rawId).strip().toLowerCase(Locale.ROOT);
            if (!id.matches("[a-z0-9_-]{1,32}")) continue;
            int threshold;
            Object rawMinimum = raw.containsKey("minimum") ? raw.get("minimum") : minimum;
            try { threshold = Integer.parseInt(String.valueOf(rawMinimum)); }
            catch (NumberFormatException exception) { continue; }
            threshold = Math.clamp(threshold, minimum, maximum);
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

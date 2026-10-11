package io.github.miklires.mreputation;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

final class ReputationStorage implements AutoCloseable {
    record Stored(int value, String name, List<ReputationEntry> history) {
        Stored { history = List.copyOf(history); }
    }
    record VoteKey(UUID voterId, UUID targetId) { }
    record StoredVote(int direction, long timestamp) { }
    record Data(Map<UUID, Stored> records, Map<VoteKey, StoredVote> votes) {
        Data { records = Map.copyOf(records); votes = Map.copyOf(votes); }
    }

    private final JavaPlugin plugin;
    private final Path file;
    private final Path backup;
    private final Path temporary;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(
            runnable -> Thread.ofPlatform().name("mReputation-storage").unstarted(runnable));
    private final AtomicReference<Data> pending = new AtomicReference<>();
    private final AtomicBoolean drainScheduled = new AtomicBoolean();
    private volatile boolean closed;

    ReputationStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = plugin.getDataFolder().toPath().resolve("data.yml");
        this.backup = plugin.getDataFolder().toPath().resolve("data.yml.bak");
        this.temporary = plugin.getDataFolder().toPath().resolve("data.yml.tmp");
    }

    Data load() {
        if (!Files.exists(file) && Files.exists(backup)) {
            plugin.getLogger().warning("data.yml is missing; recovering the last backup");
            Data recovered = loadFile(backup);
            return recovered == null ? new Data(Map.of(), Map.of()) : recovered;
        }
        Data loaded = loadFile(file);
        if (loaded != null) return loaded;
        if (!Files.exists(backup)) return new Data(Map.of(), Map.of());
        plugin.getLogger().warning("data.yml is invalid; recovering the last backup");
        Data recovered = loadFile(backup);
        return recovered == null ? new Data(Map.of(), Map.of()) : recovered;
    }

    private Data loadFile(Path source) {
        Map<UUID, Stored> output = new HashMap<>();
        Map<VoteKey, StoredVote> votes = new HashMap<>();
        if (!Files.exists(source)) return new Data(output, votes);
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(source.toFile());
        } catch (Exception exception) {
            plugin.getLogger().severe("Could not read " + source.getFileName() + ": " + exception.getMessage());
            return null;
        }
        var root = yaml.getConfigurationSection("players");
        if (root != null) {
            for (String raw : root.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(raw);
                    int value = root.getInt(raw + ".value");
                    String name = bounded(root.getString(raw + ".name", raw), 36);
                    List<ReputationEntry> history = new ArrayList<>();
                    for (Map<?, ?> entry : root.getMapList(raw + ".history")) {
                        history.add(new ReputationEntry(number(entry.get("time")), (int) number(entry.get("delta")),
                                (int) number(entry.get("value")), bounded(Objects.toString(entry.get("actor"), "unknown"), 64),
                                bounded(Objects.toString(entry.get("reason"), ""), 160)));
                    }
                    output.put(id, new Stored(value, name, history));
                } catch (Exception exception) {
                    plugin.getLogger().warning("Skipping invalid reputation record " + raw + ": " + exception.getMessage());
                }
            }
        }
        var voteRoot = yaml.getConfigurationSection("votes");
        if (voteRoot != null) for (String voterRaw : voteRoot.getKeys(false)) {
            try {
                UUID voter = UUID.fromString(voterRaw);
                var targets = voteRoot.getConfigurationSection(voterRaw);
                if (targets == null) continue;
                for (String targetRaw : targets.getKeys(false)) {
                    UUID target = UUID.fromString(targetRaw);
                    int direction = Math.clamp(targets.getInt(targetRaw + ".direction"), -1, 1);
                    long timestamp = Math.max(0L, targets.getLong(targetRaw + ".timestamp"));
                    if (direction != 0) votes.put(new VoteKey(voter, target), new StoredVote(direction, timestamp));
                }
            } catch (Exception exception) {
                plugin.getLogger().warning("Skipping invalid vote record " + voterRaw + ": " + exception.getMessage());
            }
        }
        return new Data(output, votes);
    }

    synchronized void saveAsync(Map<UUID, Stored> records, Map<VoteKey, StoredVote> votes) {
        if (closed) return;
        pending.set(new Data(records, votes));
        scheduleDrain();
    }

    private void scheduleDrain() {
        if (!drainScheduled.compareAndSet(false, true)) return;
        try {
            writer.execute(this::drain);
        } catch (RejectedExecutionException ignored) {
            drainScheduled.set(false);
        }
    }

    private void drain() {
        try {
            Data snapshot;
            while ((snapshot = pending.getAndSet(null)) != null) save(snapshot);
        } finally {
            drainScheduled.set(false);
            if (pending.get() != null && !closed) scheduleDrain();
        }
    }

    private void save(Data snapshot) {
        YamlConfiguration yaml = new YamlConfiguration();
        snapshot.records().entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparing(UUID::toString))).forEach(item -> {
            String path = "players." + item.getKey();
            yaml.set(path + ".value", item.getValue().value());
            yaml.set(path + ".name", item.getValue().name());
            List<Map<String, Object>> history = new ArrayList<>();
            for (ReputationEntry entry : item.getValue().history()) {
                Map<String, Object> serialized = new LinkedHashMap<>();
                serialized.put("time", entry.timestamp());
                serialized.put("delta", entry.delta());
                serialized.put("value", entry.value());
                serialized.put("actor", entry.actor());
                serialized.put("reason", entry.reason());
                history.add(serialized);
            }
            yaml.set(path + ".history", history);
        });
        snapshot.votes().entrySet().stream().sorted(Comparator.comparing(entry ->
                entry.getKey().voterId() + ":" + entry.getKey().targetId())).forEach(entry -> {
            String path = "votes." + entry.getKey().voterId() + "." + entry.getKey().targetId();
            yaml.set(path + ".direction", entry.getValue().direction());
            yaml.set(path + ".timestamp", entry.getValue().timestamp());
        });
        try {
            Files.createDirectories(file.getParent());
            yaml.save(temporary.toFile());
            if (Files.exists(file)) Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception exception) {
            plugin.getLogger().severe("Could not save reputation data: " + exception.getMessage());
            try { Files.deleteIfExists(temporary); } catch (Exception ignored) { }
        }
    }

    private static long number(Object value) {
        return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    }

    private static String bounded(String value, int maximum) {
        String clean = value == null ? "" : value.replaceAll("[\\p{Cntrl}&&[^\\t]]", "").strip();
        return clean.length() <= maximum ? clean : clean.substring(0, maximum);
    }

    @Override public synchronized void close() {
        closed = true;
        Data lastSnapshot = pending.getAndSet(null);
        if (lastSnapshot != null) {
            try { writer.execute(() -> save(lastSnapshot)); }
            catch (RejectedExecutionException ignored) { }
        }
        writer.shutdown();
        try {
            if (!writer.awaitTermination(5, TimeUnit.SECONDS)) {
                writer.shutdownNow();
                plugin.getLogger().warning("Reputation storage did not flush within 5 seconds");
            }
        } catch (InterruptedException exception) {
            writer.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

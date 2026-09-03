package io.github.miklires.mreputation;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
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

    private final JavaPlugin plugin;
    private final Path file;
    private final Path backup;
    private final Path temporary;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(
            runnable -> Thread.ofPlatform().name("mReputation-storage").unstarted(runnable));
    private final AtomicReference<Map<UUID, Stored>> pending = new AtomicReference<>();
    private final AtomicBoolean drainScheduled = new AtomicBoolean();
    private volatile boolean closed;

    ReputationStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = plugin.getDataFolder().toPath().resolve("data.yml");
        this.backup = plugin.getDataFolder().toPath().resolve("data.yml.bak");
        this.temporary = plugin.getDataFolder().toPath().resolve("data.yml.tmp");
    }

    Map<UUID, Stored> load() {
        if (!Files.exists(file) && Files.exists(backup)) {
            plugin.getLogger().warning("data.yml is missing; recovering the last backup");
            return loadFile(backup);
        }
        Map<UUID, Stored> loaded = loadFile(file);
        if (!loaded.isEmpty() || !Files.exists(file) || !Files.exists(backup)) return loaded;
        if (file.toFile().length() == 0L) {
            plugin.getLogger().warning("data.yml is empty; recovering the last backup");
            return loadFile(backup);
        }
        return loaded;
    }

    private Map<UUID, Stored> loadFile(Path source) {
        Map<UUID, Stored> output = new HashMap<>();
        if (!Files.exists(source)) return output;
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(source.toFile());
        } catch (Exception exception) {
            plugin.getLogger().severe("Could not read " + source.getFileName() + ": " + exception.getMessage());
            return output;
        }
        var root = yaml.getConfigurationSection("players");
        if (root == null) return output;
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
        return output;
    }

    void saveAsync(Map<UUID, Stored> snapshot) {
        if (closed) return;
        pending.set(Map.copyOf(snapshot));
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
            Map<UUID, Stored> snapshot;
            while ((snapshot = pending.getAndSet(null)) != null) save(snapshot);
        } finally {
            drainScheduled.set(false);
            if (pending.get() != null && !closed) scheduleDrain();
        }
    }

    private void save(Map<UUID, Stored> snapshot) {
        YamlConfiguration yaml = new YamlConfiguration();
        snapshot.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparing(UUID::toString))).forEach(item -> {
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

    @Override public void close() {
        closed = true;
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

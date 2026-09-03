package io.github.miklires.mreputation;

import org.bukkit.plugin.java.JavaPlugin;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class UpdateChecker {
    private static final Pattern VERSION = Pattern.compile("\\\"version_number\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private UpdateChecker() { }
    static void checkAsync(JavaPlugin plugin, String id) {
        if (id == null || !id.matches("[A-Za-z0-9_-]{3,64}")) return;
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.modrinth.com/v2/project/" + id + "/version?loaders=%5B%22paper%22%5D&game_versions=%5B%2226.2%22%5D"))
                .timeout(Duration.ofSeconds(8)).header("User-Agent", "miklires/mReputation/" + plugin.getPluginMeta().getVersion()).build();
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAccept(response -> {
            if (response.statusCode() != 200) return;
            Matcher matcher = VERSION.matcher(response.body());
            if (!matcher.find()) return;
            String available = matcher.group(1);
            try {
                if (compare(available, plugin.getPluginMeta().getVersion()) > 0)
                    plugin.getLogger().info("A newer mReputation version is available: " + available + " (Modrinth)");
            } catch (IllegalArgumentException ignored) { }
        }).exceptionally(error -> null);
    }
    static int compare(String left, String right) {
        List<Integer> a = parts(left); List<Integer> b = parts(right);
        for (int index = 0; index < Math.max(a.size(), b.size()); index++) {
            int result = Integer.compare(index < a.size() ? a.get(index) : 0, index < b.size() ? b.get(index) : 0);
            if (result != 0) return result;
        }
        return 0;
    }
    private static List<Integer> parts(String value) {
        String core = (value == null ? "" : value.strip().replaceFirst("^[vV]", "")).split("[-+]", 2)[0];
        String[] raw = core.split("\\."); List<Integer> output = new ArrayList<>();
        for (String part : raw) { if (!part.matches("\\d+")) throw new IllegalArgumentException("Invalid version"); output.add(Integer.parseInt(part)); }
        if (output.isEmpty()) throw new IllegalArgumentException("Empty version");
        return output;
    }
}

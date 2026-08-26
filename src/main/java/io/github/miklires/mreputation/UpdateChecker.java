package io.github.miklires.mreputation;
import org.bukkit.plugin.java.JavaPlugin;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
final class UpdateChecker {
 private UpdateChecker(){}
 static void checkAsync(JavaPlugin p,String id){if(id==null||id.isBlank())return;HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build().sendAsync(HttpRequest.newBuilder(URI.create("https://api.modrinth.com/v2/project/"+id+"/version?loaders=%5B%22paper%22%5D")).timeout(Duration.ofSeconds(8)).header("User-Agent","miklires/mReputation/"+p.getPluginMeta().getVersion()).build(),HttpResponse.BodyHandlers.ofString()).thenAccept(r->{if(r.statusCode()>=400)return;String m="\"version_number\":\"";int s=r.body().indexOf(m);if(s<0)return;s+=m.length();int e=r.body().indexOf('"',s);if(e>s&&!r.body().substring(s,e).equals(p.getPluginMeta().getVersion()))p.getLogger().info("A different mReputation release is available: "+r.body().substring(s,e));}).exceptionally(x->null);}
}

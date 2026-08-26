package io.github.miklires.mreputation;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;

final class ReputationStorage implements AutoCloseable {
 record Stored(int value,String name,List<ReputationEntry> history){}
 private final JavaPlugin plugin; private final File file; private final ExecutorService writer=Executors.newSingleThreadExecutor(r->Thread.ofPlatform().name("mReputation-storage").unstarted(r));
 ReputationStorage(JavaPlugin plugin){this.plugin=plugin;this.file=new File(plugin.getDataFolder(),"data.yml");}
 Map<UUID,Stored> load(){ Map<UUID,Stored> out=new HashMap<>(); YamlConfiguration y=YamlConfiguration.loadConfiguration(file); var root=y.getConfigurationSection("players"); if(root==null)return out; for(String raw:root.getKeys(false))try{UUID id=UUID.fromString(raw);int value=root.getInt(raw+".value");String name=root.getString(raw+".name",raw);List<ReputationEntry> history=new ArrayList<>();for(Map<?,?> entry:root.getMapList(raw+".history"))history.add(new ReputationEntry(number(entry.get("time")),(int)number(entry.get("delta")),(int)number(entry.get("value")),Objects.toString(entry.get("actor"),"unknown"),Objects.toString(entry.get("reason"),"")));out.put(id,new Stored(value,name,List.copyOf(history)));}catch(Exception e){plugin.getLogger().warning("Skipping invalid reputation record "+raw+": "+e.getMessage());} return out;}
 private static long number(Object value){return value instanceof Number n?n.longValue():Long.parseLong(String.valueOf(value));}
 void saveAsync(Map<UUID,Stored> snapshot){writer.execute(()->save(snapshot));}
 private void save(Map<UUID,Stored> snapshot){YamlConfiguration y=new YamlConfiguration();for(var item:snapshot.entrySet()){String path="players."+item.getKey();y.set(path+".value",item.getValue().value());y.set(path+".name",item.getValue().name());List<Map<String,Object>> history=new ArrayList<>();for(ReputationEntry e:item.getValue().history()){Map<String,Object> m=new LinkedHashMap<>();m.put("time",e.timestamp());m.put("delta",e.delta());m.put("value",e.value());m.put("actor",e.actor());m.put("reason",e.reason());history.add(m);}y.set(path+".history",history);}try{y.save(file);}catch(Exception e){plugin.getLogger().severe("Could not save reputation data: "+e.getMessage());}}
 public void close(){writer.shutdown();try{if(!writer.awaitTermination(5,TimeUnit.SECONDS))plugin.getLogger().warning("Reputation storage did not flush within 5 seconds.");}catch(InterruptedException e){Thread.currentThread().interrupt();}}
}

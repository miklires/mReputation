package io.github.miklires.mreputation;
import io.github.miklires.mreputation.api.*;
import org.bukkit.Bukkit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ReputationService implements ReputationAPI {
 private final MReputation plugin; private final ReputationStorage storage; private final Map<UUID,ReputationStorage.Stored> records=new ConcurrentHashMap<>(); private volatile ReputationPolicy policy; private volatile int historyLimit;
 ReputationService(MReputation plugin){this.plugin=plugin;this.storage=new ReputationStorage(plugin);records.putAll(storage.load());reloadPolicy();}
 void reloadPolicy(){var c=plugin.getConfig();policy=new ReputationPolicy(c.getInt("reputation.default",500),c.getInt("reputation.minimum",0),c.getInt("reputation.maximum",1000),c.getInt("reputation.positive-threshold",500),c.getInt("reputation.points-above-threshold-per-action",1));historyLimit=Math.max(1,c.getInt("reputation.history-limit",50));}
 public int get(UUID id){return records.getOrDefault(id,new ReputationStorage.Stored(policy.defaultValue(),id.toString(),List.of())).value();}
 public List<ReputationEntry> history(UUID id){return records.getOrDefault(id,new ReputationStorage.Stored(policy.defaultValue(),id.toString(),List.of())).history();}
 public boolean change(UUID id,String name,int delta,String actor,String reason){int old=get(id);return apply(id,name,old,policy.change(old,delta),actor,reason);}
 public boolean set(UUID id,String name,int value,String actor,String reason){int old=get(id);return apply(id,name,old,policy.set(value),actor,reason);}
 private synchronized boolean apply(UUID id,String name,int old,int value,String actor,String reason){if(old==value)return false;var event=new ReputationChangeEvent(id,old,value,actor,reason);Bukkit.getPluginManager().callEvent(event);if(event.isCancelled())return false;List<ReputationEntry> history=new ArrayList<>(history(id));history.add(new ReputationEntry(System.currentTimeMillis(),value-old,value,actor,safeReason(reason)));while(history.size()>historyLimit)history.removeFirst();records.put(id,new ReputationStorage.Stored(value,name,List.copyOf(history)));storage.saveAsync(Map.copyOf(records));plugin.handleZero(id,name,value);return true;}
 private static String safeReason(String reason){String text=reason==null?"":reason.strip();return text.length()>160?text.substring(0,160):text;}
 void close(){storage.close();}
}

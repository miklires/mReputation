package io.github.miklires.mreputation;
import io.github.miklires.mreputation.api.ReputationAPI;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.util.UUID;

public final class MReputation extends JavaPlugin {
 private ReputationService service; private MessageBundle messages;
 public void onEnable(){saveDefaultConfig();saveLang("en_US");saveLang("ru_RU");messages=new MessageBundle(this);service=new ReputationService(this);getServer().getServicesManager().register(ReputationAPI.class,service,this,ServicePriority.Normal);ReputationCommand c=new ReputationCommand(this);if(getCommand("reputation")!=null){getCommand("reputation").setExecutor(c);getCommand("reputation").setTabCompleter(c);}if(getConfig().getBoolean("metrics.enabled",true)&&getConfig().getInt("metrics.bstats-id",0)>0)new Metrics(this,getConfig().getInt("metrics.bstats-id"));if(getConfig().getBoolean("updates.enabled",true))io.github.miklires.mreputation.UpdateChecker.checkAsync(this,getConfig().getString("updates.modrinth-project-id",""));getLogger().info("mReputation "+getPluginMeta().getVersion()+" enabled.");}
 public void onDisable(){if(service!=null)service.close();}
 void reloadAll(){reloadConfig();messages.reload();service.reloadPolicy();}
 void handleZero(UUID id,String name,int value){if(value>getConfig().getInt("reputation.minimum",0)||!getConfig().getBoolean("zero-action.enabled",true))return;var online=Bukkit.getPlayer(id);if(online!=null&&online.hasPermission("mreputation.exempt.zero"))return;String command=getConfig().getString("zero-action.command","").replace("{player}",name);if(command.isBlank())return;getServer().getGlobalRegionScheduler().execute(this,()->Bukkit.dispatchCommand(Bukkit.getConsoleSender(),command));}
 private void saveLang(String locale){String path="lang/"+locale+".yml";if(!new File(getDataFolder(),path).exists())saveResource(path,false);}
 ReputationService service(){return service;} MessageBundle messages(){return messages;}
}

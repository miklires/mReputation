package io.github.miklires.mreputation;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
final class MessageBundle {
 private final MReputation plugin; private YamlConfiguration yaml;
 MessageBundle(MReputation plugin){this.plugin=plugin;reload();}
 void reload(){String requested=plugin.getConfig().getString("language","en_US");String locale="ru_RU".equalsIgnoreCase(requested)?"ru_RU":"en_US";String path="lang/"+locale+".yml";File file=new File(plugin.getDataFolder(),path);if(!file.exists())plugin.saveResource(path,false);yaml=YamlConfiguration.loadConfiguration(file);}
 String text(String key,String... values){String text=yaml.getString("prefix","")+yaml.getString(key,key);for(int i=0;i+1<values.length;i+=2)text=text.replace("{"+values[i]+"}",MiniMessage.miniMessage().escapeTags(values[i+1]));return text;}
}

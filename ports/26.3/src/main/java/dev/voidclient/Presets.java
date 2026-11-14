package dev.voidclient;

import java.util.*;
import java.nio.charset.StandardCharsets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;

final class Presets {
    static final Map<String,KeyMapping> keys=new LinkedHashMap<>();
    private static String location="", active="";
    private static boolean settingsKey(String k){
        return !k.startsWith("preset.") && !k.startsWith("mark.") && !k.startsWith("visualWorld.");
    }
    private static void snapshot(String prefix){
        var cfg=VoidClient.cfg;
        cfg.keySet().removeIf(k->k.toString().startsWith(prefix));
        for(String k:List.copyOf(cfg.stringPropertyNames()))if(settingsKey(k))
            cfg.setProperty(prefix+"setting."+k,cfg.getProperty(k));
        keys.forEach((id,key)->cfg.setProperty(prefix+"bind."+id,key.saveString()));
    }
    private static void restore(String prefix){
        var cfg=VoidClient.cfg;
        cfg.keySet().removeIf(k->settingsKey(k.toString()));
        for(String k:List.copyOf(cfg.stringPropertyNames()))if(k.startsWith(prefix+"setting."))
            cfg.setProperty(k.substring(prefix.length()+8),cfg.getProperty(k));
        keys.forEach((id,key)->{
            String binding=cfg.getProperty(prefix+"bind."+id);
            if(binding!=null)key.setKey(com.mojang.blaze3d.platform.InputConstants.getKey(binding));
        });
        KeyMapping.resetMapping();
    }
    private static void leave(){
        if(active.isEmpty())return;
        snapshot(active);
        restore("preset.base.");
        active="";
        VoidClient.cfg.remove("preset.active");
        VoidClient.save();
    }
    private static void enter(){
        snapshot("preset.base.");
        active="preset.profile."+location+".";
        if(VoidClient.cfg.containsKey(active+"bind.open"))restore(active);
        VoidClient.cfg.setProperty("preset.active",active);
        VoidClient.save();
    }
    static String label(){return active.isEmpty()?"Shared":"Dedicated";}
    static void tick(Minecraft c){
        if(active.isEmpty() && VoidClient.cfg.containsKey("preset.active")){
            active=VoidClient.cfg.getProperty("preset.active");
            leave();
        }
        String next="";
        if(c.player!=null && c.level!=null){
            String scope=VoidClient.scope(c);
            next=Base64.getUrlEncoder().withoutPadding().encodeToString(scope.substring(5,scope.length()-VoidClient.dim(c).length()-1).getBytes(StandardCharsets.UTF_8));
        }
        if(next.equals(location))return;
        leave();location=next;
        if(!next.isEmpty() && Boolean.parseBoolean(VoidClient.cfg.getProperty("preset.use."+location)))enter();
    }
    static void toggle(Minecraft c){
        tick(c);
        if(location.isEmpty())return;
        boolean enable=active.isEmpty();
        VoidClient.cfg.setProperty("preset.use."+location,Boolean.toString(enable));
        if(enable)enter();else leave();
    }
}

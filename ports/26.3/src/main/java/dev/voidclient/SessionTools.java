package dev.voidclient;

import net.minecraft.client.Minecraft;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class SessionTools {
    private static Object connection;
    private static int pops;
    private static long lastPop;
    static void register(VoidClient registry){
        registry.hud("statusEffects","Status / potion icons",c->null);
        registry.hud("totemPops","Personal totem pops",c->pops==0?null:
            "Totems popped  "+pops+"  ("+(System.nanoTime()-lastPop)/1_000_000_000L+"s ago)");
        registry.hud("deathPoint","Last death waypoint",c->c.player.getLastDeathLocation().map(death->{
            var p=death.pos();
            return "Death  "+death.dimension().identifier().getPath()+"  "+p.getX()+" / "+p.getY()+" / "+p.getZ();
        }).orElse(null));
        Preferences.toggle(registry,"breakGuard","Tool break guard (sneak to override)");
        ClientTickEvents.START_CLIENT_TICK.register(c->session(c.getConnection()));
    }
    private static void session(Object current){
        if(current!=connection){connection=current;pops=0;}
    }
    public static void recordPop(Object current){session(current);pops++;lastPop=System.nanoTime();}
    public static boolean protectTool(){
        var c=Minecraft.getInstance();
        if(c.player==null || !VoidClient.switchOn("breakGuard") || c.player.isShiftKeyDown() || c.player.isCreative())return false;
        var item=c.player.getMainHandItem();
        return item.isDamageableItem() && item.getMaxDamage()-item.getDamageValue()<=1;
    }
}

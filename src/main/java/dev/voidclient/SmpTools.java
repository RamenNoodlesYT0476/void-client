package dev.voidclient;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.registry.Registries;

final class SmpTools {
    private static final String[] SITES={"Base","Build","Meetup"};
    private static final String[] DIRECTIONS={"N","NE","E","SE","S","SW","W","NW"};

    private static String scope(MinecraftClient c){return VoidClient.scope(c)+".smp.";}
    private static int slot(MinecraftClient c){return VoidClient.number(scope(c)+"selected",SITES.length);}
    private record Site(int x,int y,int z){
        String coordinates(){return x+" "+y+" "+z;}
    }
    private static Site here(MinecraftClient c){
        var p=c.player.getBlockPos();
        return new Site(p.getX(),p.getY(),p.getZ());
    }
    private static Site saved(MinecraftClient c){
        var parts=VoidClient.cfg.getProperty(scope(c)+"site."+slot(c),"").split(" ");
        if(parts.length!=3)return null;
        try{return new Site(Integer.parseInt(parts[0]),Integer.parseInt(parts[1]),Integer.parseInt(parts[2]));}
        catch(NumberFormatException ignored){return null;}
    }
    private static String route(MinecraftClient c){
        var site=saved(c);if(site==null)return null;
        double dx=site.x()+.5-c.player.getX(), dz=site.z()+.5-c.player.getZ();
        long distance=Math.round(Math.hypot(dx,dz));
        String heading=DIRECTIONS[Math.floorMod((int)Math.round(Math.atan2(dx,-dz)/(Math.PI/4)),8)];
        return SITES[slot(c)]+"  "+(distance==0?"Here":distance+"m "+heading)+"   Y "+site.y();
    }
    private static void draft(MinecraftClient c,String label,Site site){
        c.setScreen(new ChatScreen(label+" at "+site.coordinates()+" ("+VoidClient.dim(c)+")",false));
    }
    private static String supplies(MinecraftClient c){
        String target=VoidClient.cfg.getProperty(scope(c)+"material");if(target==null)return null;
        int count=0;
        var inv=c.player.getInventory();
        for(int i=0;i<36;i++){
            var stack=inv.getStack(i);
            if(!stack.isEmpty() && Registries.ITEM.getId(stack.getItem()).toString().equals(target))count+=stack.getCount();
        }
        var offhand=c.player.getOffHandStack();
        if(!offhand.isEmpty() && Registries.ITEM.getId(offhand.getItem()).toString().equals(target))count+=offhand.getCount();
        return "Supply  "+target.substring(target.indexOf(':')+1).replace('_',' ')+"  ×"+count;
    }
    static void register(VoidClient r){
        r.action("smpSite","Cycle landmark (Base / Build / Meetup)",c->{
            int next=(slot(c)+1)%SITES.length;
            VoidClient.set(scope(c)+"selected",Integer.toString(next));
            SurvivalKit.message(c,SITES[next]+(saved(c)==null?" • empty":" • "+saved(c).coordinates()));
        });
        r.action("smpSave","Save selected landmark here",c->{
            VoidClient.set(scope(c)+"site."+slot(c),here(c).coordinates());
            SurvivalKit.message(c,SITES[slot(c)]+" saved at "+here(c).coordinates());
        });
        r.action("smpClear","Clear selected landmark",c->{
            VoidClient.cfg.remove(scope(c)+"site."+slot(c));VoidClient.save();
            SurvivalKit.message(c,SITES[slot(c)]+" cleared");
        });
        r.action("smpShareSite","Draft selected landmark in chat",c->{
            var site=saved(c);
            if(site==null){SurvivalKit.message(c,"Save "+SITES[slot(c)]+" first");return;}
            draft(c,SITES[slot(c)],site);
        });
        r.action("smpMeet","Draft my location in chat",c->draft(c,"Meet me",here(c)));
        r.action("smpMaterial","Track held item / empty hand to clear",c->{
            var stack=c.player.getMainHandStack();
            if(stack.isEmpty()){
                VoidClient.cfg.remove(scope(c)+"material");VoidClient.save();
                SurvivalKit.message(c,"Supply tracker cleared");
            }else{
                VoidClient.set(scope(c)+"material",Registries.ITEM.getId(stack.getItem()).toString());
                SurvivalKit.message(c,supplies(c));
            }
        });
        r.hud("smpRoute","Landmark distance and bearing",SmpTools::route);
        r.hud("smpSupply","Tracked supply count",SmpTools::supplies);
    }
}

package dev.voidclient;

import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.registry.Registries;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.block.Blocks;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

final class VisualTools {
    private record Spot(Vec3d position,String name,long seen) {}
    private record Target(Vec3d position,int color) {}
    private static final Map<UUID,Spot> sightings=new HashMap<>(), departed=new HashMap<>();
    private static final List<Target> blocks=new ArrayList<>();
    private static final String DEFAULT_BLOCKS="minecraft:diamond_ore,minecraft:deepslate_diamond_ore,minecraft:ancient_debris";
    private static Object level;
    private static String scope="";
    private static int tick;
    private static int range(){return 24*(1+VoidClient.number("visual.range",4));}
    private static int color(){return VoidClient.colors[VoidClient.number("visual.color",4)];}
    static String key(String id){return "visualWorld."+scope+"."+id;}
    private static void toggle(VoidClient r,String id,String label){
        r.action("switch."+id,label,c->{sync(c);VoidClient.set(key(id),Boolean.toString(!VoidClient.switchOn(id)));});
    }
    static void register(VoidClient r){
        toggle(r,"visualPlayers","Player ESP");
        toggle(r,"visualBots","Practice bot ESP");
        toggle(r,"visualItems","Dropped item ESP");
        toggle(r,"visualLogout","Logout spots");
        toggle(r,"visualXray","X-ray selected blocks");
        toggle(r,"visualBases","Exposed crystal bases");
        r.action("visualBlock","Add / remove targeted X-ray block",c->{
            sync(c);
            if(!(c.crosshairTarget instanceof BlockHitResult hit) || hit.getType()!=HitResult.Type.BLOCK){SurvivalKit.message(c,"Aim at a block first");return;}
            String id=Registries.BLOCK.getId(c.world.getBlockState(hit.getBlockPos()).getBlock()).toString();
            var ids=list("blocks",DEFAULT_BLOCKS);boolean added=ids.add(id);if(!added)ids.remove(id);
            VoidClient.set(key("blocks"),String.join(",",ids));SurvivalKit.message(c,(added?"Tracking ":"Removed ")+id);
        });
        r.action("visualBot","Mark / unmark targeted practice bot",c->{
            sync(c);
            if(!(c.crosshairTarget instanceof EntityHitResult hit) || !(hit.getEntity() instanceof PlayerEntity p)){SurvivalKit.message(c,"Aim at a practice player first");return;}
            var ids=list("bots","");String id=p.getUuid().toString();boolean added=ids.add(id);if(!added)ids.remove(id);
            VoidClient.set(key("bots"),String.join(",",ids));SurvivalKit.message(c,(added?"Marked ":"Unmarked ")+p.getName().getString());
        });
        r.hud("chunkPosition","Chunk and local coordinates",c->{
            var p=c.player.getBlockPos();
            return "Chunk  "+(p.getX()>>4)+" / "+(p.getZ()>>4)+"   Local  "+(p.getX()&15)+" / "+(p.getZ()&15);
        });
        r.hud("connectionPing","Connection latency",c->{
            var info=c.getNetworkHandler()==null?null:c.getNetworkHandler().getPlayerListEntry(c.player.getUuid());
            return c.getServer()!=null || info==null?null:"Ping  "+info.getLatency()+" ms";
        });
        ClientTickEvents.END_CLIENT_TICK.register(VisualTools::update);
    }
    private static Set<String> list(String name,String defaults){
        var values=new LinkedHashSet<>(Arrays.asList(VoidClient.cfg.getProperty(key(name),defaults).split(",")));
        values.remove("");return values;
    }
    private static void sync(MinecraftClient c){
        if(level==c.world)return;
        level=c.world;blocks.clear();sightings.clear();departed.clear();tick=0;
        if(level==null){scope="";return;}
        String s=VoidClient.scope(c);
        scope=Base64.getUrlEncoder().withoutPadding().encodeToString(s.substring(5,s.length()-1-VoidClient.dim(c).length()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private static void update(MinecraftClient c){
        sync(c);if(c.player==null || c.world==null)return;
        if(VoidClient.switchOn("visualLogout") && c.getNetworkHandler()!=null){
            long now=System.nanoTime();
            var connection=c.getNetworkHandler();
            departed.entrySet().removeIf(e->now-e.getValue().seen()>600_000_000_000L || connection.getPlayerListEntry(e.getKey())!=null);
            sightings.entrySet().removeIf(e->{
                if(now-e.getValue().seen()>30_000_000_000L)return true;
                if(connection.getPlayerListEntry(e.getKey())!=null)return false;
                departed.put(e.getKey(),new Spot(e.getValue().position(),e.getValue().name(),now));return true;
            });
            for(var p:c.world.getPlayers())if(p!=c.player && connection.getPlayerListEntry(p.getUuid())!=null)
                sightings.put(p.getUuid(),new Spot(p.getEntityPos(),p.getName().getString(),now));
        }else{sightings.clear();departed.clear();}
        if(++tick%10!=0)return;
        blocks.clear();
        boolean xray=VoidClient.switchOn("visualXray"),bases=VoidClient.switchOn("visualBases");
        if(!xray && !bases)return;
        var ids=list("blocks",DEFAULT_BLOCKS);
        var origin=c.player.getBlockPos();int radius=4+2*VoidClient.number("visual.scan",4);
        for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++)for(int y=-radius;y<=radius;y++){
            var p=origin.add(x,y,z);
            if(!c.world.isChunkLoaded(p))continue;
            var state=c.world.getBlockState(p);
            if(state.isAir())continue;
            if(xray && ids.contains(Registries.BLOCK.getId(state.getBlock()).toString()))
                blocks.add(new Target(Vec3d.ofCenter(p),0xff62d9ff));
            else if(bases && (state.isOf(Blocks.OBSIDIAN)||state.isOf(Blocks.BEDROCK)) && c.world.isAir(p.up()))
                blocks.add(new Target(Vec3d.ofCenter(p),0xffbd78ff));
        }
        blocks.sort(Comparator.comparingDouble(b->b.position().squaredDistanceTo(c.player.getEntityPos())));
        if(blocks.size()>64)blocks.subList(64,blocks.size()).clear();
    }
    private record Pixel(int x,int y){}
    private static Pixel project(MinecraftClient c,DrawContext g,Vec3d pos){
        var camera=c.gameRenderer.getCamera();
        var delta=pos.subtract(camera.getCameraPos());
        var forward=camera.getHorizontalPlane();
        if(delta.x*forward.x()+delta.y*forward.y()+delta.z*forward.z()<0.1)return null;
        var ndc=c.gameRenderer.project(pos);
        if(!Double.isFinite(ndc.x)||!Double.isFinite(ndc.y)||Math.abs(ndc.x)>1||Math.abs(ndc.y)>1)return null;
        return new Pixel((int)((ndc.x+1)*g.getScaledWindowWidth()/2),(int)((1-ndc.y)*g.getScaledWindowHeight()/2));
    }
    static void draw(MinecraftClient c,DrawContext g){
        if(level!=c.world)return;
        boolean players=VoidClient.switchOn("visualPlayers"), bots=VoidClient.switchOn("visualBots"), items=VoidClient.switchOn("visualItems");
        int distance=range();double limit=distance*distance;
        if(players||bots||items){
            var ids=list("bots","");
            for(var entity:c.world.getEntities()){
                if(entity==c.player || !entity.isAlive() || entity.squaredDistanceTo(c.player)>limit)continue;
                String label;int ink;
                if(entity instanceof PlayerEntity p){
                    boolean bot=ids.contains(p.getUuid().toString()) || p.getName().getString().equalsIgnoreCase("TheobaldTheBot");
                    if(bot?!bots:!players)continue;
                    label=p.getName().getString();ink=color();
                }else if(items && entity instanceof ItemEntity item){
                    label=item.getStack().getName().getString()+" ×"+item.getStack().getCount();ink=0xffeed683;
                }else continue;
                var top=project(c,g,entity.getEntityPos().add(0,entity.getHeight(),0));
                var foot=project(c,g,entity.getEntityPos());
                if(top==null || foot==null)continue;
                int h=Math.max(3,foot.y-top.y),w=Math.max(2,(int)(h*entity.getWidth()/Math.max(.1,entity.getHeight())/2));
                int x=top.x,y=top.y;
                g.fill(x-w,y,x+w+1,y+1,ink);g.fill(x-w,y+h,x+w+1,y+h+1,ink);
                g.fill(x-w,y,x-w+1,y+h,ink);g.fill(x+w,y,x+w+1,y+h,ink);
                g.drawCenteredTextWithShadow(c.textRenderer,label,x,y-9,ink);
            }
        }
        if(VoidClient.switchOn("visualLogout"))for(var spot:departed.values()){
            if(spot.position().squaredDistanceTo(c.player.getEntityPos())>limit)continue;
            var point=project(c,g,spot.position());if(point!=null)
                g.drawCenteredTextWithShadow(c.textRenderer,spot.name()+" • last seen • "+(System.nanoTime()-spot.seen())/1_000_000_000L+"s ago",point.x,point.y,0xffc7b3ff);
        }
        for(var block:blocks){
            if(!VoidClient.switchOn(block.color()==0xff62d9ff?"visualXray":"visualBases"))continue;
            var p=project(c,g,block.position());if(p==null)continue;
            g.fill(p.x-2,p.y,p.x+3,p.y+1,block.color());g.fill(p.x,p.y-2,p.x+1,p.y+3,block.color());
        }
    }
}

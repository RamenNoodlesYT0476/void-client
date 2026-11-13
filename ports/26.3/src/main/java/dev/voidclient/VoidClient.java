package dev.voidclient;

import dev.voidclient.api.VoidPlugin;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.function.*;
import org.slf4j.LoggerFactory;

public class VoidClient implements ClientModInitializer, VoidPlugin.Registry {
    record Module(String id,String label,Function<Minecraft,String> hud,Consumer<Minecraft> action){}
    static final List<Module> modules = new ArrayList<>();
    static final Properties cfg = new Properties();
    static final Path config = FabricLoader.getInstance().getConfigDir().resolve("void-client.properties");
    static final int[] colors = {0xFFFF375F,0xFFBD78FF,0xFF4BAEFF,0xFFB4BDCD};
    static final String[] names = {"Neon red","Ultraviolet","Electric blue","Steel grey"};
    static int number(String key,int max){try{return Math.floorMod(Integer.parseInt(cfg.getProperty(key,"0")),max);}catch(NumberFormatException e){return 0;}}
    static int accent(){return colors[number("accent",colors.length)];}
    static void set(String key,String value){cfg.setProperty(key,value);save();}
    static void save(){try{
        Path temp=Files.createTempFile(config.getParent(),"void-", ".tmp");
        try(var out=Files.newOutputStream(temp)){cfg.store(out,"Void Client");}
        Files.move(temp,config,StandardCopyOption.REPLACE_EXISTING);
    }catch(IOException e){LoggerFactory.getLogger("VoidClient").warn("Could not save settings",e);}}
    static boolean enabled(Module m){return Boolean.parseBoolean(cfg.getProperty("module."+m.id,"true"));}
    static boolean switchOn(String id){return Boolean.parseBoolean(cfg.getProperty("switch."+id,"false"));}
    private void add(Module m){if(modules.stream().anyMatch(x->x.id.equals(m.id)))throw new IllegalArgumentException("Duplicate module "+m.id);modules.add(m);}
    public void hud(String id,String name,Function<Minecraft,String> f){add(new Module(id,name,f,null));}
    public void action(String id,String name,Consumer<Minecraft> f){add(new Module(id,name,null,f));}
    static String pos(Minecraft c){var p=c.player.blockPosition();return p.getX()+" / "+p.getY()+" / "+p.getZ();}
    static String dim(Minecraft c){return c.level.dimension().identifier().toString();}
    static String scope(Minecraft c){return "mark."+(c.getSingleplayerServer()!=null?c.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath():c.getCurrentServer()!=null?c.getCurrentServer().ip:"local")+"."+dim(c);}
    public void onInitializeClient(){
        if(Files.exists(config))try(var in=Files.newInputStream(config)){cfg.load(in);}catch(Exception e){LoggerFactory.getLogger("VoidClient").warn("Could not load settings",e);}
        Combat.register(this);
        Preferences.register(this);
        ModuleIcons.registerActions(this);
        hud("xyz","Coordinates",c->"XYZ  "+pos(c));
        hud("heading","Direction",c->"Facing  "+c.player.getDirection().getName());
        hud("sprintStatus","Sprint display",c->c.player.isSprinting()?"Sprint  •  active":"Sprint  •  idle");
        hud("fps","Frame rate",c->"FPS  "+c.getFps());
        hud("gear","Tool durability",c->{var s=c.player.getMainHandItem();return s.isDamageableItem()?"Tool  "+(s.getMaxDamage()-s.getDamageValue())+" / "+s.getMaxDamage():null;});
        hud("portal","Portal coordinates",c->{var d=c.level.dimension();if(d!=Level.OVERWORLD && d!=Level.NETHER)return null;double f=d==Level.NETHER?8:0.125;return (f==8?"Overworld":"Nether")+"  "+(int)Math.floor(c.player.getX()*f)+" / "+(int)Math.floor(c.player.getZ()*f);});
        hud("waypoint","Saved waypoint",c->{var v=cfg.getProperty(scope(c));return v==null?null:"Mark  "+v;});
        action("mark","Mark this position",c->{set(scope(c),pos(c));c.player.sendOverlayMessage(Component.literal("Void • Waypoint saved"));});
        action("copy","Copy coordinates",c->{c.keyboardHandler.setClipboard(pos(c));c.player.sendOverlayMessage(Component.literal("Void • Coordinates copied"));});
                for(String[] sw:new String[][]{{"sprint","Auto sprint"},{"tool","Auto select tool"},{"respawn","Auto respawn"}}){
            action("switch."+sw[0],sw[1],c->set("switch."+sw[0],String.valueOf(!switchOn(sw[0]))));
        }
        ClientTickEvents.END_CLIENT_TICK.register(c->{
            var p=c.player;if(p==null||c.level==null)return;
            if(switchOn("respawn")&&c.gui.screen() instanceof net.minecraft.client.gui.screens.DeathScreen){p.respawn();c.gui.setScreen(null);return;}
            if(c.gui.screen()!=null)return;
            if(switchOn("sprint")&&p.input.hasForwardImpulse()&&!p.isShiftKeyDown()&&!p.isUsingItem()&&!p.horizontalCollision&&(p.getFoodData().getFoodLevel()>6||p.getAbilities().mayfly))p.setSprinting(true);
            if(switchOn("tool")&&c.options.keyAttack.isDown()&&c.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit&&hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK){
                var state=c.level.getBlockState(hit.getBlockPos());var inv=p.getInventory();int best=inv.getSelectedSlot();
                for(int i=0;i<9;i++){var a=inv.getItem(i);var b=inv.getItem(best);boolean good=a.isCorrectToolForDrops(state),prev=b.isCorrectToolForDrops(state);
                    if((state.requiresCorrectToolForDrops()&&good&&!prev)||((!state.requiresCorrectToolForDrops()||good==prev)&&a.getDestroySpeed(state)>b.getDestroySpeed(state)))best=i;}
                inv.setSelectedSlot(best);
            }
        });
        for(var p:FabricLoader.getInstance().getEntrypoints("voidclient",VoidPlugin.class))p.register(this);
        var category=KeyMapping.Category.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("voidclient","general"));
        var key=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.voidclient.open", InputConstants.KEY_RBRACKET, category));
        var shortcuts=new LinkedHashMap<KeyMapping,Module>();
        for(var module:modules) if(module.id().startsWith("switch.")){
            var bind=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.voidclient."+module.id(), InputConstants.UNKNOWN.getValue(), category));
            shortcuts.put(bind,module);
        }
        ClientTickEvents.END_CLIENT_TICK.register(c->{
            while(key.consumeClick() && c.player!=null)c.gui.setScreen(new Console());
            shortcuts.forEach((bind,module)->{
                while(bind.consumeClick())if(c.player!=null && c.gui.screen()==null){
                    module.action().accept(c);
                    c.player.sendOverlayMessage(Component.literal(module.label()+(switchOn(module.id().substring(7))?" on":" off")));
                }
            });
        });        HudElementRegistry.addLast(net.minecraft.resources.Identifier.fromNamespaceAndPath("voidclient","hud"),(g,t)->renderHud(g));
    }
    static void renderHud(GuiGraphicsExtractor g){
        var c=Minecraft.getInstance();
        if(c.player==null || c.level==null || c.getDebugOverlay().showDebugScreen()
            || c.gui.screen() instanceof Console || c.gui.screen() instanceof HudPosition || number("hud.hidden",2)==1)return;
        HudLayout.draw(g,false);
    }
}


package dev.voidclient;

import dev.voidclient.api.VoidPlugin;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import org.lwjgl.glfw.GLFW;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.function.*;
import org.slf4j.LoggerFactory;

public class VoidClient implements ClientModInitializer, VoidPlugin.Registry {
    record Module(String id,String label,Function<MinecraftClient,String> hud,Consumer<MinecraftClient> action){}
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
    static boolean enabled(Module m){return Boolean.parseBoolean(cfg.getProperty("module."+m.id,switch(m.id){case "chunkPosition","connectionPing","deathPoint","anchorCharges","damagePreview","packSpace","blockLight" -> "false"; default -> "true";}));}
    static boolean switchOn(String id){return Boolean.parseBoolean(cfg.getProperty(id.startsWith("visual")?VisualTools.key(id):"switch."+id,"false"));}
    private void add(Module m){if(modules.stream().anyMatch(x->x.id.equals(m.id)))throw new IllegalArgumentException("Duplicate module "+m.id);modules.add(m);}
    public void hud(String id,String name,Function<MinecraftClient,String> f){add(new Module(id,name,f,null));}
    public void action(String id,String name,Consumer<MinecraftClient> f){add(new Module(id,name,null,f));}
    static String pos(MinecraftClient c){var p=c.player.getBlockPos();return p.getX()+" / "+p.getY()+" / "+p.getZ();}
    static String dim(MinecraftClient c){return c.world.getRegistryKey().getValue().toString();}
    static String scope(MinecraftClient c){return "mark."+(c.getServer()!=null?c.getServer().getSavePath(net.minecraft.util.WorldSavePath.ROOT).toAbsolutePath():c.getCurrentServerEntry()!=null?c.getCurrentServerEntry().address:"local")+"."+dim(c);}
    public void onInitializeClient(){
        if(Files.exists(config))try(var in=Files.newInputStream(config)){cfg.load(in);}catch(Exception e){LoggerFactory.getLogger("VoidClient").warn("Could not load settings",e);}
        Combat.register(this);
        FieldUtilities.register(this);
        SurvivalKit.register(this);
        AnchorSync.register(this);
        SessionTools.register(this);
        VisualTools.register(this);
        SmpTools.register(this);
        Preferences.register(this);
        ModuleIcons.registerActions(this);
        hud("xyz","Coordinates",c->"XYZ  "+pos(c));
        hud("heading","Direction",c->"Facing  "+c.player.getHorizontalFacing().asString());
        hud("sprintStatus","Sprint display",c->c.player.isSprinting()?"Sprint  •  active":"Sprint  •  idle");
        hud("fps","Frame rate",c->"FPS  "+c.getCurrentFps());
        hud("gear","Tool durability",c->{var s=c.player.getMainHandStack();return s.isDamageable()?"Tool  "+(s.getMaxDamage()-s.getDamage())+" / "+s.getMaxDamage():null;});
        hud("portal","Portal coordinates",c->{var d=c.world.getRegistryKey();if(d!=World.OVERWORLD && d!=World.NETHER)return null;double f=d==World.NETHER?8:0.125;return (f==8?"Overworld":"Nether")+"  "+(int)Math.floor(c.player.getX()*f)+" / "+(int)Math.floor(c.player.getZ()*f);});
        hud("waypoint","Saved waypoint",c->{var v=cfg.getProperty(scope(c));return v==null?null:"Mark  "+v;});
        action("mark","Mark this position",c->{set(scope(c),pos(c));c.player.sendMessage(Text.literal("Void • Waypoint saved"),true);});
        action("copy","Copy coordinates",c->{c.keyboard.setClipboard(pos(c));c.player.sendMessage(Text.literal("Void • Coordinates copied"),true);});
                for(String[] sw:new String[][]{{"sprint","Auto sprint"},{"tool","Auto select tool"},{"respawn","Auto respawn"}}){
            action("switch."+sw[0],sw[1],c->set("switch."+sw[0],String.valueOf(!switchOn(sw[0]))));
        }
        ClientTickEvents.END_CLIENT_TICK.register(c->{
            var p=c.player;if(p==null||c.world==null)return;
            if(switchOn("respawn")&&c.currentScreen instanceof net.minecraft.client.gui.screen.DeathScreen){p.requestRespawn();c.setScreen(null);return;}
            if(c.currentScreen!=null)return;
            if(switchOn("sprint")&&p.input.hasForwardMovement()&&!p.isSneaking()&&!p.isUsingItem()&&!p.horizontalCollision&&(p.getHungerManager().getFoodLevel()>6||p.getAbilities().allowFlying))p.setSprinting(true);
            if(switchOn("tool")&&c.options.attackKey.isPressed()&&c.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult hit&&hit.getType()==net.minecraft.util.hit.HitResult.Type.BLOCK){
                var state=c.world.getBlockState(hit.getBlockPos());var inv=p.getInventory();int best=inv.getSelectedSlot();
                for(int i=0;i<9;i++){var a=inv.getStack(i);var b=inv.getStack(best);boolean good=a.isSuitableFor(state),prev=b.isSuitableFor(state);
                    if((state.isToolRequired()&&good&&!prev)||((!state.isToolRequired()||good==prev)&&a.getMiningSpeedMultiplier(state)>b.getMiningSpeedMultiplier(state)))best=i;}
                inv.setSelectedSlot(best);
            }
        });
        for(var p:FabricLoader.getInstance().getEntrypoints("voidclient",VoidPlugin.class))p.register(this);
        var category=KeyBinding.Category.create(net.minecraft.util.Identifier.of("voidclient","general"));
        var key=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.voidclient.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_BRACKET, category));
        var shortcuts=new LinkedHashMap<KeyBinding,Module>();
        for(var module:modules) if(module.id().startsWith("switch.") || module.id().equals("anchorCycle")){
            var bind=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.voidclient."+module.id(), InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));
            shortcuts.put(bind,module);
            Presets.keys.put(module.id(),bind);
        }
        Presets.keys.put("open",key);
        ClientTickEvents.START_CLIENT_TICK.register(Presets::tick);
        ClientTickEvents.END_CLIENT_TICK.register(c->{
            while(key.wasPressed() && c.player!=null)c.setScreen(new Console());
            shortcuts.forEach((bind,module)->{
                while(bind.wasPressed())if(c.player!=null && c.currentScreen==null){
                    module.action().accept(c);
                    if (!module.id().startsWith("switch.")) continue;
                    c.player.sendMessage(Text.literal(module.label()+(switchOn(module.id().substring(7))?" on":" off")),true);
                }
            });
        });        HudElementRegistry.addLast(net.minecraft.util.Identifier.of("voidclient","hud"),(g,t)->renderHud(g));
    }
    static void renderHud(DrawContext g){
        var c=MinecraftClient.getInstance();
        if(c.player==null || c.world==null || c.options.hudHidden || c.getDebugHud().shouldShowDebugHud()
            || c.currentScreen instanceof Console || c.currentScreen instanceof HudPosition || number("hud.hidden",2)==1)return;
        HudLayout.draw(g,false);
        SurvivalKit.drawRadar(g);
        VisualTools.draw(c,g);
    }
}

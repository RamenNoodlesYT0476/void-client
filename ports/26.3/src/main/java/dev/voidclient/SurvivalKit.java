package dev.voidclient;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

final class SurvivalKit {
    private static final Direction[] SIDES = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final List<Hole> holes = new ArrayList<>();
    private record Hole(int x, int z, boolean bedrock) {}
    private static String preview;
    private Player owner;
    private BlockPos origin;
    private int side, nextPlace;

    static void register(VoidClient registry) {
        Preferences.toggle(registry, "surround", "Surround once");
        Preferences.toggle(registry, "holeRadar", "Hole ESP radar");
        Preferences.toggle(registry, "armor", "Auto Armor");
        Preferences.toggle(registry, "mend", "Auto Mend");
        registry.hud("damagePreview", "Crystal damage preview", c -> preview);
        registry.hud("packSpace", "Inventory space", c -> {
            int empty = 0;
            for (int i = 0; i < 36; i++) if (c.player.getInventory().getItem(i).isEmpty()) empty++;
            return "Pack  " + empty + " free slots";
        });
        registry.hud("blockLight", "Block light at feet", c -> "Block light  " +
            c.level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, c.player.blockPosition()));
        registry.action("torch", "Select torches", c -> {
            for (int i = 0; i < 9; i++) {
                var s = c.player.getInventory().getItem(i);
                if (s.is(Items.TORCH) || s.is(Items.SOUL_TORCH)) { c.player.getInventory().setSelectedSlot(i); return; }
            }
            message(c, "No torches in hotbar");
        });
        ClientTickEvents.END_CLIENT_TICK.register(new SurvivalKit()::tick);
    }

    static void message(Minecraft c, String text) {
        c.player.sendOverlayMessage(net.minecraft.network.chat.Component.literal(text));
    }

    private void tick(Minecraft c) {
        var p = c.player;
        if (p != owner) { owner = p; origin = null; holes.clear(); preview = null; }
        if (p == null || c.level == null || c.gameMode == null || !p.isAlive()) return;
        if (p.tickCount % 5 == 0) {
            preview = Boolean.parseBoolean(VoidClient.cfg.getProperty("module.damagePreview","false")) ? estimate(c) : null;
            holes.clear();
            if (VoidClient.switchOn("holeRadar")) scan(c);
        }
        if (!VoidClient.switchOn("surround")) origin = null;
        if (c.gui.screen() != null || p.isSpectator() || p.isUsingItem() || p.containerMenu != p.inventoryMenu) return;
        if (VoidClient.switchOn("surround")) place(c);
        if (p.tickCount % 8 != 0) return;
        if (VoidClient.switchOn("armor")) equip(c);
        if (VoidClient.switchOn("mend") && p.getXRot() >= 70 && p.getMainHandItem().is(Items.EXPERIENCE_BOTTLE)) {
            for (var slot : ARMOR) {
                var s = p.getItemBySlot(slot);
                int goal = 70 + 10 * VoidClient.number("mend.goal",4);
                if (s.isDamageableItem() && (s.getMaxDamage()-s.getDamageValue())*100 < goal*s.getMaxDamage()
                    && s.getEnchantments().keySet().stream().anyMatch(e -> e.is(Enchantments.MENDING))) {
                    c.gameMode.useItem(p, InteractionHand.MAIN_HAND); break;
                }
            }
        }
    }

    private void place(Minecraft c) {
        var p = c.player;
        if (origin == null) { origin = p.blockPosition(); side = 0; nextPlace = p.tickCount; }
        if (p.tickCount < nextPlace) return;
        if (p.blockPosition().equals(origin) && side < SIDES.length) {
            var dest = origin.relative(SIDES[side++]);
            if (!c.level.getBlockState(dest).isAir()) return;
            var inv = p.getInventory();
            for (int i=0; i<9; i++) if (inv.getItem(i).is(Items.OBSIDIAN)) {
                for (var dir : Direction.values()) {
                    var support = dest.relative(dir);
                    if (!c.level.getBlockState(support).isFaceSturdy(c.level,support,dir.getOpposite())) continue;
                    var face = Vec3.atCenterOf(dest).add(dir.getStepX()*.5,dir.getStepY()*.5,dir.getStepZ()*.5);
                    int previous = inv.getSelectedSlot();
                    inv.setSelectedSlot(i);
                    try { c.gameMode.useItemOn(p,InteractionHand.MAIN_HAND,new BlockHitResult(face,dir.getOpposite(),support,false)); }
                    finally { inv.setSelectedSlot(previous); }
                    nextPlace = p.tickCount + 2 + 2*VoidClient.number("surround.delay",4);
                    return;
                }
                return;
            }
            message(c,"Surround needs obsidian in the hotbar");
        }
        origin = null;
        VoidClient.set("switch.surround","false");
    }

    private static void equip(Minecraft c) {
        var p=c.player;
        var menu=p.inventoryMenu;
        if (!menu.getCarried().isEmpty()) return;
        for (int i=0;i<ARMOR.length;i++) {
            var armor=ARMOR[i]; var current=p.getItemBySlot(armor);
            int remaining=current.getMaxDamage()-current.getDamageValue();
            if (!current.isEmpty() && (!current.isDamageableItem() || remaining*100 > current.getMaxDamage()*5*VoidClient.number("armor.replace",4))) continue;
            var dest=menu.slots.get(5+i);
            if (!dest.mayPickup(p)) continue;
            for(var slot:menu.slots) {
                if(slot.container!=p.getInventory() || slot.getContainerSlot()>=36) continue;
                var s=slot.getItem(); var eq=s.get(DataComponents.EQUIPPABLE);
                if(eq==null || eq.slot()!=armor || !s.isDamageableItem() || !dest.mayPlace(s) ||
                    (!current.isEmpty() && s.getMaxDamage()-s.getDamageValue()<=remaining)) continue;
                for(int id:new int[]{slot.index,dest.index})
                    c.gameMode.handleContainerInput(menu.containerId,id,0,ContainerInput.PICKUP,p);
                if(!menu.getCarried().isEmpty()) c.gameMode.handleContainerInput(menu.containerId,slot.index,0,ContainerInput.PICKUP,p);
                return;
            }
        }
    }

    private static String estimate(Minecraft c) {
        Vec3 center;
        if (c.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof EndCrystal crystal) center=crystal.position();
        else if(c.hitResult instanceof BlockHitResult hit && hit.getType()==HitResult.Type.BLOCK) {
            var state=c.level.getBlockState(hit.getBlockPos());
            if(!state.is(Blocks.OBSIDIAN) && !state.is(Blocks.BEDROCK)) return null;
            center=Vec3.atBottomCenterOf(hit.getBlockPos().above());
        } else return null;
        var target=c.level.players().stream().filter(p->p!=c.player && p.isAlive() && !p.isSpectator() && p.distanceToSqr(center)<=144)
            .min(java.util.Comparator.comparingDouble(p->p.distanceToSqr(center))).orElse(null);
        return "Est. blast HP  You "+damage(center,c.player)+(target==null?"":"  "+target.getName().getString()+" "+damage(center,target));
    }

    private static int damage(Vec3 center, Player p) {
        double distance=Math.sqrt(p.distanceToSqr(center))/12;
        if(distance>1) return 0;
        double impact=(1-distance)*ServerExplosion.getSeenPercent(center,p);
        double raw = (int)((impact * impact + impact) * 42 + 1);
        raw = switch (p.level().getDifficulty()) {
            case PEACEFUL -> 0;
            case EASY -> Math.min(raw, raw / 2 + 1);
            case HARD -> raw * 1.5;
            default -> raw;
        };
        if (p.isCreative() || p.isSpectator()) return 0;
        double armor = p.getArmorValue();
        double toughness = p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS);
        double effectiveArmor = Math.min(20, Math.max(armor * .2, armor - raw / (2 + toughness / 4)));
        double damage = raw * (1 - effectiveArmor / 25);
        var resistance = p.getEffect(net.minecraft.world.effect.MobEffects.RESISTANCE);
        if (resistance != null) damage *= Math.max(0, 1 - .2 * (1 + resistance.getAmplifier()));
        int protection = 0;
        for (var slot : ARMOR) for (var enchant : p.getItemBySlot(slot).getEnchantments().entrySet()) {
            if (enchant.getKey().is(Enchantments.PROTECTION)) protection += enchant.getIntValue();
            if (enchant.getKey().is(Enchantments.BLAST_PROTECTION)) protection += 2 * enchant.getIntValue();
        }
        return (int)Math.ceil(damage * (1 - Math.min(protection,20) / 25.0));
    }

    private static void scan(Minecraft c) {
        int range=3+VoidClient.number("hole.range",4);
        for(int x=-range;x<=range;x++) for(int z=-range;z<=range;z++) {
            var pos=c.player.blockPosition().offset(x,0,z);
            if(!c.level.isEmptyBlock(pos)||!c.level.isEmptyBlock(pos.above())) continue;
            var floor=c.level.getBlockState(pos.below());
            if(!floor.is(Blocks.BEDROCK)&&!floor.is(Blocks.OBSIDIAN)) continue;
            boolean bedrock=floor.is(Blocks.BEDROCK),closed=true;
            for(var dir:SIDES) {
                var wall=c.level.getBlockState(pos.relative(dir));
                if(!wall.is(Blocks.BEDROCK)&&!wall.is(Blocks.OBSIDIAN)){closed=false;break;}
                bedrock &= wall.is(Blocks.BEDROCK);
            }
            if(closed) holes.add(new Hole(x,z,bedrock));
        }
    }

    static void drawRadar(GuiGraphicsExtractor g) {
        if(!VoidClient.switchOn("holeRadar") || holes.isEmpty()) return;
        int x=g.guiWidth()/2,y=g.guiHeight()/2+36;
        g.fill(x,y,x+2,y+2,0xffeeeeee);
        for(var hole:holes){
            int hx=x+hole.x()*4,hy=y+hole.z()*4;
            g.fill(hx-1,hy-1,hx+2,hy+2,hole.bedrock()?0xff57b7ff:0xffbd78ff);
        }
    }
}

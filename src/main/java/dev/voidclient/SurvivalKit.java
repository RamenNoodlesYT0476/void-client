package dev.voidclient;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.util.Hand;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.block.Blocks;
import net.minecraft.world.explosion.ExplosionImpl;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.List;

final class SurvivalKit {
    private static final Direction[] SIDES = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final List<Hole> holes = new ArrayList<>();
    private record Hole(int x, int z, boolean bedrock) {}
    private static String preview;
    private PlayerEntity owner;
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
            for (int i = 0; i < 36; i++) if (c.player.getInventory().getStack(i).isEmpty()) empty++;
            return "Pack  " + empty + " free slots";
        });
        registry.hud("blockLight", "Block light at feet", c -> "Block light  " +
            c.world.getLightLevel(net.minecraft.world.LightType.BLOCK, c.player.getBlockPos()));
        registry.action("torch", "Select torches", c -> {
            for (int i = 0; i < 9; i++) {
                var s = c.player.getInventory().getStack(i);
                if (s.isOf(Items.TORCH) || s.isOf(Items.SOUL_TORCH)) { c.player.getInventory().setSelectedSlot(i); return; }
            }
            message(c, "No torches in hotbar");
        });
        ClientTickEvents.END_CLIENT_TICK.register(new SurvivalKit()::tick);
    }

    static void message(MinecraftClient c, String text) {
        c.player.sendMessage(net.minecraft.text.Text.literal(text),true);
    }

    private void tick(MinecraftClient c) {
        var p = c.player;
        if (p != owner) { owner = p; origin = null; holes.clear(); preview = null; }
        if (p == null || c.world == null || c.interactionManager == null || !p.isAlive()) return;
        if (p.age % 5 == 0) {
            preview = Boolean.parseBoolean(VoidClient.cfg.getProperty("module.damagePreview","false")) ? estimate(c) : null;
            holes.clear();
            if (VoidClient.switchOn("holeRadar")) scan(c);
        }
        if (!VoidClient.switchOn("surround")) origin = null;
        if (c.currentScreen != null || p.isSpectator() || p.isUsingItem() || p.currentScreenHandler != p.playerScreenHandler) return;
        if (VoidClient.switchOn("surround")) place(c);
        if (p.age % 8 != 0) return;
        if (VoidClient.switchOn("armor")) equip(c);
        if (VoidClient.switchOn("mend") && p.getPitch() >= 70 && p.getMainHandStack().isOf(Items.EXPERIENCE_BOTTLE)) {
            for (var slot : ARMOR) {
                var s = p.getEquippedStack(slot);
                int goal = 70 + 10 * VoidClient.number("mend.goal",4);
                if (s.isDamageable() && (s.getMaxDamage()-s.getDamage())*100 < goal*s.getMaxDamage()
                    && s.getEnchantments().getEnchantments().stream().anyMatch(e -> e.matchesKey(Enchantments.MENDING))) {
                    c.interactionManager.interactItem(p, Hand.MAIN_HAND); break;
                }
            }
        }
    }

    private void place(MinecraftClient c) {
        var p = c.player;
        if (origin == null) { origin = p.getBlockPos(); side = 0; nextPlace = p.age; }
        if (p.age < nextPlace) return;
        if (p.getBlockPos().equals(origin) && side < SIDES.length) {
            var dest = origin.offset(SIDES[side++]);
            if (!c.world.getBlockState(dest).isAir()) return;
            var inv = p.getInventory();
            for (int i=0; i<9; i++) if (inv.getStack(i).isOf(Items.OBSIDIAN)) {
                for (var dir : Direction.values()) {
                    var support = dest.offset(dir);
                    if (!c.world.getBlockState(support).isSideSolidFullSquare(c.world,support,dir.getOpposite())) continue;
                    var face = Vec3d.ofCenter(dest).add(dir.getOffsetX()*.5,dir.getOffsetY()*.5,dir.getOffsetZ()*.5);
                    int previous = inv.getSelectedSlot();
                    inv.setSelectedSlot(i);
                    try { c.interactionManager.interactBlock(p,Hand.MAIN_HAND,new BlockHitResult(face,dir.getOpposite(),support,false)); }
                    finally { inv.setSelectedSlot(previous); }
                    nextPlace = p.age + 2 + 2*VoidClient.number("surround.delay",4);
                    return;
                }
                return;
            }
            message(c,"Surround needs obsidian in the hotbar");
        }
        origin = null;
        VoidClient.set("switch.surround","false");
    }

    private static void equip(MinecraftClient c) {
        var p=c.player;
        var menu=p.playerScreenHandler;
        if (!menu.getCursorStack().isEmpty()) return;
        for (int i=0;i<ARMOR.length;i++) {
            var armor=ARMOR[i]; var current=p.getEquippedStack(armor);
            int remaining=current.getMaxDamage()-current.getDamage();
            if (!current.isEmpty() && (!current.isDamageable() || remaining*100 > current.getMaxDamage()*5*VoidClient.number("armor.replace",4))) continue;
            var dest=menu.slots.get(5+i);
            if (!dest.canTakeItems(p)) continue;
            for(var slot:menu.slots) {
                if(slot.inventory!=p.getInventory() || slot.getIndex()>=36) continue;
                var s=slot.getStack(); var eq=s.get(DataComponentTypes.EQUIPPABLE);
                if(eq==null || eq.slot()!=armor || !s.isDamageable() || !dest.canInsert(s) ||
                    (!current.isEmpty() && s.getMaxDamage()-s.getDamage()<=remaining)) continue;
                for(int id:new int[]{slot.id,dest.id})
                    c.interactionManager.clickSlot(menu.syncId,id,0,SlotActionType.PICKUP,p);
                if(!menu.getCursorStack().isEmpty()) c.interactionManager.clickSlot(menu.syncId,slot.id,0,SlotActionType.PICKUP,p);
                return;
            }
        }
    }

    private static String estimate(MinecraftClient c) {
        Vec3d center;
        if (c.crosshairTarget instanceof EntityHitResult hit && hit.getEntity() instanceof EndCrystalEntity crystal) center=crystal.getEntityPos();
        else if(c.crosshairTarget instanceof BlockHitResult hit && hit.getType()==HitResult.Type.BLOCK) {
            var state=c.world.getBlockState(hit.getBlockPos());
            if(!state.isOf(Blocks.OBSIDIAN) && !state.isOf(Blocks.BEDROCK)) return null;
            center=Vec3d.ofBottomCenter(hit.getBlockPos().up());
        } else return null;
        var target=c.world.getPlayers().stream().filter(p->p!=c.player && p.isAlive() && !p.isSpectator() && p.squaredDistanceTo(center)<=144)
            .min(java.util.Comparator.comparingDouble(p->p.squaredDistanceTo(center))).orElse(null);
        return "Est. blast HP  You "+damage(center,c.player)+(target==null?"":"  "+target.getName().getString()+" "+damage(center,target));
    }

    private static int damage(Vec3d center, PlayerEntity p) {
        double distance=Math.sqrt(p.squaredDistanceTo(center))/12;
        if(distance>1) return 0;
        double impact=(1-distance)*ExplosionImpl.calculateReceivedDamage(center,p);
        double raw = (int)((impact * impact + impact) * 42 + 1);
        raw = switch (p.getEntityWorld().getDifficulty()) {
            case PEACEFUL -> 0;
            case EASY -> Math.min(raw, raw / 2 + 1);
            case HARD -> raw * 1.5;
            default -> raw;
        };
        if (p.isCreative() || p.isSpectator()) return 0;
        double armor = p.getArmor();
        double toughness = p.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.ARMOR_TOUGHNESS);
        double effectiveArmor = Math.min(20, Math.max(armor * .2, armor - raw / (2 + toughness / 4)));
        double damage = raw * (1 - effectiveArmor / 25);
        var resistance = p.getStatusEffect(net.minecraft.entity.effect.StatusEffects.RESISTANCE);
        if (resistance != null) damage *= Math.max(0, 1 - .2 * (1 + resistance.getAmplifier()));
        int protection = 0;
        for (var slot : ARMOR) for (var enchant : p.getEquippedStack(slot).getEnchantments().getEnchantmentEntries()) {
            if (enchant.getKey().matchesKey(Enchantments.PROTECTION)) protection += enchant.getIntValue();
            if (enchant.getKey().matchesKey(Enchantments.BLAST_PROTECTION)) protection += 2 * enchant.getIntValue();
        }
        return (int)Math.ceil(damage * (1 - Math.min(protection,20) / 25.0));
    }

    private static void scan(MinecraftClient c) {
        int range=3+VoidClient.number("hole.range",4);
        for(int x=-range;x<=range;x++) for(int z=-range;z<=range;z++) {
            var pos=c.player.getBlockPos().add(x,0,z);
            if(!c.world.isAir(pos)||!c.world.isAir(pos.up())) continue;
            var floor=c.world.getBlockState(pos.down());
            if(!floor.isOf(Blocks.BEDROCK)&&!floor.isOf(Blocks.OBSIDIAN)) continue;
            boolean bedrock=floor.isOf(Blocks.BEDROCK),closed=true;
            for(var dir:SIDES) {
                var wall=c.world.getBlockState(pos.offset(dir));
                if(!wall.isOf(Blocks.BEDROCK)&&!wall.isOf(Blocks.OBSIDIAN)){closed=false;break;}
                bedrock &= wall.isOf(Blocks.BEDROCK);
            }
            if(closed) holes.add(new Hole(x,z,bedrock));
        }
    }

    static void drawRadar(DrawContext g) {
        if(!VoidClient.switchOn("holeRadar") || holes.isEmpty()) return;
        int x=g.getScaledWindowWidth()/2,y=g.getScaledWindowHeight()/2+36;
        g.fill(x,y,x+2,y+2,0xffeeeeee);
        for(var hole:holes){
            int hx=x+hole.x()*4,hy=y+hole.z()*4;
            g.fill(hx-1,hy-1,hx+2,hy+2,hole.bedrock()?0xff57b7ff:0xffbd78ff);
        }
    }
}

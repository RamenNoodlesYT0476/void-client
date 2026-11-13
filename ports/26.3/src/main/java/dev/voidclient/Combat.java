package dev.voidclient;

import java.util.Comparator;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.AABB;

final class Combat {
    private static int nextCrystalTick;
    private static Player lastPlayer;

    static void register(VoidClient registry) {
        toggle(registry, "sword", "Kill Aura");
        toggle(registry, "crystal", "Crystal aura");
        toggle(registry, "totem", "Auto totem");
        ClientTickEvents.END_CLIENT_TICK.register(Combat::tick);
    }

    private static void toggle(VoidClient registry, String key, String name) {
        registry.action("switch." + key, name, client ->
            VoidClient.set("switch." + key, Boolean.toString(!VoidClient.switchOn(key))));
    }

    private static double range() {
        return 2 + VoidClient.number("combat.range", 3) * 0.5;
    }

    private static void tick(Minecraft client) {
        var player = client.player;
        if (player == null || client.level == null || client.gameMode == null ||
            client.gui.screen() != null || !player.isAlive() || player.isSpectator()) return;
        if (lastPlayer != player) {
            lastPlayer = player;
            nextCrystalTick = 0;
        }
        if (VoidClient.switchOn("totem") && player.containerMenu == player.inventoryMenu &&
            !player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
            for (var slot : player.inventoryMenu.slots) {
                if (slot.container == player.getInventory() && slot.getContainerSlot() < 36 &&
                    slot.getItem().is(Items.TOTEM_OF_UNDYING)) {
                    client.gameMode.handleContainerInput(player.inventoryMenu.containerId, slot.index, 40, ContainerInput.SWAP, player);
                    return;
                }
            }
        }
        if (player.isUsingItem()) return;
        if (!VoidClient.switchOn("sword") && !VoidClient.switchOn("crystal")) return;
        double reach = Math.min(range(), player.entityInteractionRange());
        var target = client.level.players().stream().filter(other -> other != player && other.isAlive() &&
            !other.isSpectator() && !other.isCreative() && !player.isAlliedTo(other) &&
            player.distanceToSqr(other) <= 36 && player.hasLineOfSight(other))
            .min(Comparator.comparingDouble(other -> player.distanceToSqr(other))).orElse(null);
        if (target == null) return;
        if (VoidClient.switchOn("sword") && (VoidClient.number("combat.weapon", 2) == 1 || player.getMainHandItem().is(ItemTags.SWORDS)) &&
            player.distanceToSqr(target) <= reach * reach && player.getAttackStrengthScale(0) >= (0.95f - 0.1f * VoidClient.number("combat.cooldown", 3))) {
            attack(client, target);
            return;
        }
        InteractionHand hand = player.getMainHandItem().is(Items.END_CRYSTAL) ? InteractionHand.MAIN_HAND :
            player.getOffhandItem().is(Items.END_CRYSTAL) ? InteractionHand.OFF_HAND : null;
        if (!VoidClient.switchOn("crystal") || player.tickCount < nextCrystalTick) return;
        var nearby = client.level.getEntitiesOfClass(EndCrystal.class, player.getBoundingBox().inflate(reach),
            crystal -> player.distanceToSqr(crystal) <= reach * reach && player.hasLineOfSight(crystal) &&
                target.distanceToSqr(crystal) < player.distanceToSqr(crystal));
        if (!nearby.isEmpty() && VoidClient.number("crystal.mode", 3) != 1) {
            attack(client, nearby.stream().min(Comparator.comparingDouble(crystal -> target.distanceToSqr(crystal))).orElseThrow());
            nextCrystalTick = player.tickCount + 2 + 2 * VoidClient.number("crystal.delay", 4);
        } else if (hand != null && VoidClient.number("crystal.mode", 3) != 2 && client.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            var pos = hit.getBlockPos();
            var state = client.level.getBlockState(pos);
            var center = net.minecraft.world.phys.Vec3.atBottomCenterOf(pos.above());
            if ((state.is(Blocks.OBSIDIAN) || state.is(Blocks.BEDROCK)) && client.level.isEmptyBlock(pos.above()) &&
                target.distanceToSqr(center) < player.distanceToSqr(center) &&
                client.level.getEntities(null, new AABB(pos.above()).expandTowards(0, 1, 0)).isEmpty()) {
                if (client.gameMode.useItemOn(player, hand, hit).consumesAction()) player.swing(hand,net.minecraft.world.item.component.SwingAnimation.DEFAULT,true);
                nextCrystalTick = player.tickCount + 2 + 2 * VoidClient.number("crystal.delay", 4);
            }
        }
    }

    private static void attack(Minecraft client, Entity entity) {
        client.gameMode.attack(client.player, entity);
        client.player.swing(InteractionHand.MAIN_HAND,net.minecraft.world.item.component.SwingAnimation.DEFAULT,true);
    }
}


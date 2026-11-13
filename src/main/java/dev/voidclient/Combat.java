package dev.voidclient;

import java.util.Comparator;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;

final class Combat {
    private static int nextCrystalTick;
    private static PlayerEntity lastPlayer;

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

    private static void tick(MinecraftClient client) {
        var player = client.player;
        if (player == null || client.world == null || client.interactionManager == null ||
            client.currentScreen != null || !player.isAlive() || player.isSpectator()) return;
        if (lastPlayer != player) {
            lastPlayer = player;
            nextCrystalTick = 0;
        }
        if (VoidClient.switchOn("totem") && player.currentScreenHandler == player.playerScreenHandler &&
            !player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            for (var slot : player.playerScreenHandler.slots) {
                if (slot.inventory == player.getInventory() && slot.getIndex() < 36 &&
                    slot.getStack().isOf(Items.TOTEM_OF_UNDYING)) {
                    client.interactionManager.clickSlot(player.playerScreenHandler.syncId, slot.id, 40, SlotActionType.SWAP, player);
                    return;
                }
            }
        }
        if (player.isUsingItem()) return;
        if (!VoidClient.switchOn("sword") && !VoidClient.switchOn("crystal")) return;
        double reach = Math.min(range(), player.getEntityInteractionRange());
        var target = client.world.getPlayers().stream().filter(other -> other != player && other.isAlive() &&
            !other.isSpectator() && !other.isCreative() && !player.isTeammate(other) &&
            player.squaredDistanceTo(other) <= 36 && player.canSee(other))
            .min(Comparator.comparingDouble(other -> player.squaredDistanceTo(other))).orElse(null);
        if (target == null) return;
        if (VoidClient.switchOn("sword") && (VoidClient.number("combat.weapon", 2) == 1 || player.getMainHandStack().isIn(ItemTags.SWORDS)) &&
            player.squaredDistanceTo(target) <= reach * reach && player.getAttackCooldownProgress(0) >= (0.95f - 0.1f * VoidClient.number("combat.cooldown", 3))) {
            attack(client, target);
            return;
        }
        Hand hand = player.getMainHandStack().isOf(Items.END_CRYSTAL) ? Hand.MAIN_HAND :
            player.getOffHandStack().isOf(Items.END_CRYSTAL) ? Hand.OFF_HAND : null;
        if (!VoidClient.switchOn("crystal") || player.age < nextCrystalTick) return;
        var nearby = client.world.getEntitiesByClass(EndCrystalEntity.class, player.getBoundingBox().expand(reach),
            crystal -> player.squaredDistanceTo(crystal) <= reach * reach && player.canSee(crystal) &&
                target.squaredDistanceTo(crystal) < player.squaredDistanceTo(crystal));
        if (!nearby.isEmpty() && VoidClient.number("crystal.mode", 3) != 1) {
            attack(client, nearby.stream().min(Comparator.comparingDouble(crystal -> target.squaredDistanceTo(crystal))).orElseThrow());
            nextCrystalTick = player.age + 2 + 2 * VoidClient.number("crystal.delay", 4);
        } else if (hand != null && VoidClient.number("crystal.mode", 3) != 2 && client.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            var pos = hit.getBlockPos();
            var state = client.world.getBlockState(pos);
            var center = net.minecraft.util.math.Vec3d.ofBottomCenter(pos.up());
            if ((state.isOf(Blocks.OBSIDIAN) || state.isOf(Blocks.BEDROCK)) && client.world.isAir(pos.up()) &&
                target.squaredDistanceTo(center) < player.squaredDistanceTo(center) &&
                client.world.getOtherEntities(null, new Box(pos.up()).stretch(0, 1, 0)).isEmpty()) {
                if (client.interactionManager.interactBlock(player, hand, hit).isAccepted()) player.swingHand(hand);
                nextCrystalTick = player.age + 2 + 2 * VoidClient.number("crystal.delay", 4);
            }
        }
    }

    private static void attack(MinecraftClient client, Entity entity) {
        client.interactionManager.attackEntity(client.player, entity);
        client.player.swingHand(Hand.MAIN_HAND);
    }
}


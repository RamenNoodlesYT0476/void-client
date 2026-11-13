package dev.voidclient;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.inventory.ContainerInput;

final class FieldUtilities {
    private int returnSlot = -1;
    private int mealSlot;
    private net.minecraft.world.entity.player.Player owner;

    static void register(VoidClient registry) {
        Preferences.toggle(registry, "eat", "Auto Eat");
        Preferences.toggle(registry, "gap", "Auto Golden Apple");
        Preferences.toggle(registry, "replenish", "Auto Replenish");
        Preferences.toggle(registry, "walk", "Auto Walk");
        Preferences.toggle(registry, "jump", "Auto Jump");
        Preferences.toggle(registry, "sneak", "Sneak");
        Preferences.toggle(registry, "autoWeapon", "Auto Weapon");
        Preferences.toggle(registry, "shear", "Auto Shearer");
        Preferences.toggle(registry, "breed", "Auto Breed");
        Preferences.toggle(registry, "tagMob", "Auto Nametag");
        Preferences.toggle(registry, "expThrow", "EXP Thrower");
        Preferences.toggle(registry, "lookDown", "Enderman Look");
        var utilities = new FieldUtilities();
        ClientTickEvents.START_CLIENT_TICK.register(utilities::input);
        ClientTickEvents.END_CLIENT_TICK.register(utilities::tick);
    }

    private boolean walking, jumping, sneaking, eating;
    private void input(Minecraft client) {
        var p = client.player;
        boolean active = p != null && p.isAlive() && !p.isSpectator() && client.gui.screen() == null;
        walking = hold(client.options.keyUp, active && VoidClient.switchOn("walk"), walking);
        jumping = hold(client.options.keyJump, active && VoidClient.switchOn("jump"), jumping);
        sneaking = hold(client.options.keyShift, active && VoidClient.switchOn("sneak"), sneaking);
        eating = hold(client.options.keyUse, active && p == owner && returnSlot >= 0 &&
            p.getInventory().getSelectedSlot() == mealSlot && p.isUsingItem(), eating);
    }

    private boolean hold(net.minecraft.client.KeyMapping key, boolean enabled, boolean previouslyHeld) {
        if (enabled || previouslyHeld) key.setDown(enabled);
        return enabled;
    }

    private void tick(Minecraft client) {
        var player = client.player;
        if (owner != player) {
            owner = player;
            returnSlot = -1;
        }
        if (player == null || client.level == null || client.gameMode == null) return;
        var inventory = player.getInventory();
        if (returnSlot >= 0) {
            if (inventory.getSelectedSlot() != mealSlot) returnSlot = -1;
            else if (!player.isUsingItem()) {
                inventory.setSelectedSlot(returnSlot);
                returnSlot = -1;
            }
        }
        if (client.gui.screen() != null || !player.isAlive() || player.isSpectator()) return;
        if (player.isUsingItem() || player.containerMenu != player.inventoryMenu) return;

        if (client.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit) {
            var target = hit.getEntity();
            var held = player.getMainHandItem();
            if (VoidClient.switchOn("lookDown") && target instanceof net.minecraft.world.entity.monster.EnderMan) {
                player.setXRot(90);
            }
            if (player.tickCount % 10 == 0 && target.isAlive()) {
                boolean shear = VoidClient.switchOn("shear") && held.is(Items.SHEARS)
                    && target instanceof net.minecraft.world.entity.animal.sheep.Sheep sheep && sheep.readyForShearing();
                boolean breed = VoidClient.switchOn("breed") && target instanceof net.minecraft.world.entity.animal.Animal animal
                    && animal.getAge() == 0 && !animal.isInLove() && animal.isFood(held);
                boolean tag = VoidClient.switchOn("tagMob") && target instanceof net.minecraft.world.entity.Mob
                    && !target.hasCustomName() && held.is(Items.NAME_TAG) && held.has(DataComponents.CUSTOM_NAME);
                if ((shear || breed || tag) && client.gameMode.interact(player, target, hit, InteractionHand.MAIN_HAND).consumesAction()) return;
            }
        }
        if (VoidClient.switchOn("expThrow") && player.tickCount % 5 == 0 &&
            player.getMainHandItem().is(Items.EXPERIENCE_BOTTLE) && player.getXRot() >= 70) {
            client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
            return;
        }
        boolean apple = VoidClient.switchOn("gap") && player.getHealth() <= 4 + 2 * VoidClient.number("alerts.health", 4)
            && !player.hasEffect(net.minecraft.world.effect.MobEffects.ABSORPTION);
        boolean hungry = VoidClient.switchOn("eat") && player.getFoodData().getFoodLevel() <= 14 + VoidClient.number("food.threshold", 5);
        if (apple || hungry) {
            for (int slot = 0; slot < 9; slot++) {
                var stack = inventory.getItem(slot);
                boolean golden = stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE);
                if (apple ? !golden : golden || !stack.has(DataComponents.FOOD) || !stack.has(DataComponents.CONSUMABLE)) continue;
                int previous = inventory.getSelectedSlot();
                inventory.setSelectedSlot(slot);
                if (client.gameMode.useItem(player, InteractionHand.MAIN_HAND).consumesAction()) {
                    returnSlot = previous;
                    mealSlot = slot;
                    return;
                }
                inventory.setSelectedSlot(previous);
            }
        }
        if (VoidClient.switchOn("autoWeapon") && client.options.keyAttack.isDown()
            && client.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit
            && hit.getEntity() instanceof net.minecraft.world.entity.LivingEntity
            && !player.getMainHandItem().is(ItemTags.SWORDS)) {
            for (int slot = 0; slot < 9; slot++) {
                if (inventory.getItem(slot).is(ItemTags.SWORDS)) {
                    inventory.setSelectedSlot(slot);
                    break;
                }
            }
        }
        if (!VoidClient.switchOn("replenish") || player.tickCount % 10 != 0) return;
        for (int hotbar = 0; hotbar < 9; hotbar++) {
            var partial = inventory.getItem(hotbar);
            if (partial.isEmpty() || partial.getMaxStackSize() <= 1 || partial.getCount() > 4 + 4 * VoidClient.number("refill.threshold", 4)) continue;
            for (var slot : player.inventoryMenu.slots) {
                if (slot.container != inventory || slot.getContainerSlot() < 9 || slot.getContainerSlot() >= 36) continue;
                var reserve = slot.getItem();
                if (reserve.getCount() <= partial.getCount() || !ItemStack.isSameItemSameComponents(partial, reserve)) continue;
                client.gameMode.handleContainerInput(player.inventoryMenu.containerId, slot.index, hotbar, ContainerInput.SWAP, player);
                return;
            }
        }
    }
}


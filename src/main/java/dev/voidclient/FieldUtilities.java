package dev.voidclient;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.util.Hand;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.slot.SlotActionType;

final class FieldUtilities {
    private int returnSlot = -1;
    private int mealSlot;
    private net.minecraft.entity.player.PlayerEntity owner;

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
    private void input(MinecraftClient client) {
        var p = client.player;
        boolean active = p != null && p.isAlive() && !p.isSpectator() && client.currentScreen == null;
        walking = hold(client.options.forwardKey, active && VoidClient.switchOn("walk"), walking);
        jumping = hold(client.options.jumpKey, active && VoidClient.switchOn("jump"), jumping);
        sneaking = hold(client.options.sneakKey, active && VoidClient.switchOn("sneak"), sneaking);
        eating = hold(client.options.useKey, active && p == owner && returnSlot >= 0 &&
            p.getInventory().getSelectedSlot() == mealSlot && p.isUsingItem(), eating);
    }

    private boolean hold(net.minecraft.client.option.KeyBinding key, boolean enabled, boolean previouslyHeld) {
        if (enabled || previouslyHeld) key.setPressed(enabled);
        return enabled;
    }

    private void tick(MinecraftClient client) {
        var player = client.player;
        if (owner != player) {
            owner = player;
            returnSlot = -1;
        }
        if (player == null || client.world == null || client.interactionManager == null) return;
        var inventory = player.getInventory();
        if (returnSlot >= 0) {
            if (inventory.getSelectedSlot() != mealSlot) returnSlot = -1;
            else if (!player.isUsingItem()) {
                inventory.setSelectedSlot(returnSlot);
                returnSlot = -1;
            }
        }
        if (client.currentScreen != null || !player.isAlive() || player.isSpectator()) return;
        if (player.isUsingItem() || player.currentScreenHandler != player.playerScreenHandler) return;

        if (client.crosshairTarget instanceof net.minecraft.util.hit.EntityHitResult hit) {
            var target = hit.getEntity();
            var held = player.getMainHandStack();
            if (VoidClient.switchOn("lookDown") && target instanceof net.minecraft.entity.mob.EndermanEntity) {
                player.setPitch(90);
            }
            if (player.age % 10 == 0 && target.isAlive()) {
                boolean shear = VoidClient.switchOn("shear") && held.isOf(Items.SHEARS)
                    && target instanceof net.minecraft.entity.passive.SheepEntity sheep && sheep.isShearable();
                boolean breed = VoidClient.switchOn("breed") && target instanceof net.minecraft.entity.passive.AnimalEntity animal
                    && animal.getBreedingAge() == 0 && !animal.isInLove() && animal.isBreedingItem(held);
                boolean tag = VoidClient.switchOn("tagMob") && target instanceof net.minecraft.entity.mob.MobEntity
                    && !target.hasCustomName() && held.isOf(Items.NAME_TAG) && held.contains(DataComponentTypes.CUSTOM_NAME);
                if ((shear || breed || tag) && client.interactionManager.interactEntity(player, target, Hand.MAIN_HAND).isAccepted()) return;
            }
        }
        if (VoidClient.switchOn("expThrow") && player.age % 5 == 0 &&
            player.getMainHandStack().isOf(Items.EXPERIENCE_BOTTLE) && player.getPitch() >= 70) {
            client.interactionManager.interactItem(player, Hand.MAIN_HAND);
            return;
        }
        boolean apple = VoidClient.switchOn("gap") && player.getHealth() <= 4 + 2 * VoidClient.number("alerts.health", 4)
            && !player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.ABSORPTION);
        boolean hungry = VoidClient.switchOn("eat") && player.getHungerManager().getFoodLevel() <= 14 + VoidClient.number("food.threshold", 5);
        if (apple || hungry) {
            for (int slot = 0; slot < 9; slot++) {
                var stack = inventory.getStack(slot);
                boolean golden = stack.isOf(Items.GOLDEN_APPLE) || stack.isOf(Items.ENCHANTED_GOLDEN_APPLE);
                if (apple ? !golden : golden || !stack.contains(DataComponentTypes.FOOD) || !stack.contains(DataComponentTypes.CONSUMABLE)) continue;
                int previous = inventory.getSelectedSlot();
                inventory.setSelectedSlot(slot);
                if (client.interactionManager.interactItem(player, Hand.MAIN_HAND).isAccepted()) {
                    returnSlot = previous;
                    mealSlot = slot;
                    return;
                }
                inventory.setSelectedSlot(previous);
            }
        }
        if (VoidClient.switchOn("autoWeapon") && client.options.attackKey.isPressed()
            && client.crosshairTarget instanceof net.minecraft.util.hit.EntityHitResult hit
            && hit.getEntity() instanceof net.minecraft.entity.LivingEntity
            && !player.getMainHandStack().isIn(ItemTags.SWORDS)) {
            for (int slot = 0; slot < 9; slot++) {
                if (inventory.getStack(slot).isIn(ItemTags.SWORDS)) {
                    inventory.setSelectedSlot(slot);
                    break;
                }
            }
        }
        if (!VoidClient.switchOn("replenish") || player.age % 10 != 0) return;
        for (int hotbar = 0; hotbar < 9; hotbar++) {
            var partial = inventory.getStack(hotbar);
            if (partial.isEmpty() || partial.getMaxCount() <= 1 || partial.getCount() > 4 + 4 * VoidClient.number("refill.threshold", 4)) continue;
            for (var slot : player.playerScreenHandler.slots) {
                if (slot.inventory != inventory || slot.getIndex() < 9 || slot.getIndex() >= 36) continue;
                var reserve = slot.getStack();
                if (reserve.getCount() <= partial.getCount() || !ItemStack.areItemsAndComponentsEqual(partial, reserve)) continue;
                client.interactionManager.clickSlot(player.playerScreenHandler.syncId, slot.id, hotbar, SlotActionType.SWAP, player);
                return;
            }
        }
    }
}

package dev.voidclient;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

final class Preferences {
    private Preferences() {}

    static String choice(String key, String... values) {
        return values[VoidClient.number(key, values.length)];
    }

    static void cycle(String key, int count) {
        VoidClient.set(key, Integer.toString((VoidClient.number(key, count) + 1) % count));
    }

    static List<VoidClient.Module> group(int index) {
        return VoidClient.modules.stream().filter(module -> category(module) == index).toList();
    }

    private static int category(VoidClient.Module module) {
        if (module.hud() != null) return 2;
        return switch (module.id()) {
            case "switch.sword", "switch.crystal", "switch.totem" -> 0;
            case "switch.lowHealth", "switch.durabilityAlert" -> 3;
            default -> 1;
        };
    }

    static void toggle(VoidClient registry, String id, String name) {
        registry.action("switch." + id, name, client -> VoidClient.set("switch." + id,
            Boolean.toString(!VoidClient.switchOn(id))));
    }

    static void register(VoidClient registry) {
        toggle(registry, "lowHealth", "Low health alert");
        toggle(registry, "durabilityAlert", "Low durability alert");
        ClientTickEvents.END_CLIENT_TICK.register(new Alerts()::tick);
    }

    private static final class Alerts {
        private net.minecraft.world.entity.player.Player previous;
        private boolean healthWarning, toolWarning;

        void tick(Minecraft client) {
            var player = client.player;
            if (previous != player) {
                previous = player;
                healthWarning = toolWarning = false;
            }
            if (player == null) return;
            var tool = player.getMainHandItem();
            boolean health = VoidClient.switchOn("lowHealth") && player.isAlive() &&
                player.getHealth() <= 4 + 2 * VoidClient.number("alerts.health", 4);
            boolean worn = VoidClient.switchOn("durabilityAlert") && tool.isDamageableItem() &&
                tool.getMaxDamage() - tool.getDamageValue() <= Math.max(1, tool.getMaxDamage() / 10);
            if ((health && !healthWarning) || (worn && !toolWarning)) {
                String warning = health && worn ? "Low health and low tool durability" :
                    health ? "Low health" : "Tool durability below 10%";
                player.sendOverlayMessage(Component.literal("Void • " + warning));
                if (VoidClient.number("alerts.sound", 2) == 0)
                    player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 0.7f, 1f);
            }
            healthWarning = health;
            toolWarning = worn;
        }
    }
}


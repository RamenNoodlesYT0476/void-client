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

        if(module.id().startsWith("switch.visual") || module.id().startsWith("visual"))return 4;
        if (module.hud() != null) return 2;

        return switch (module.id()) {

            case "switch.anchorSync", "anchorCycle", "switch.surround", "switch.holeRadar", "switch.armor", "switch.mend", "switch.gap", "switch.autoWeapon", "switch.sword", "switch.crystal", "switch.totem", "switch.blastBuffer" -> 0;

            case "switch.lowHealth", "switch.durabilityAlert", "switch.crystalWarning", "switch.totemReserve" -> 3;

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

        toggle(registry, "crystalWarning", "Nearby crystal warning");

        toggle(registry, "totemReserve", "Low totem reserve");

        ClientTickEvents.END_CLIENT_TICK.register(new Alerts()::tick);

    }



    private static final class Alerts {

        private net.minecraft.world.entity.player.Player previous;

        private boolean healthWarning, toolWarning, crystalWarning, reserveWarning;



        void tick(Minecraft client) {

            var player = client.player;

            if (previous != player) {

                previous = player;

                healthWarning = toolWarning = crystalWarning = reserveWarning = false;

            }

            if (player == null) return;

            var tool = player.getMainHandItem();

            boolean health = VoidClient.switchOn("lowHealth") && player.isAlive() &&

                player.getHealth() <= 4 + 2 * VoidClient.number("alerts.health", 4);

            boolean worn = VoidClient.switchOn("durabilityAlert") && tool.isDamageableItem() &&

                tool.getMaxDamage() - tool.getDamageValue() <= Math.max(1, tool.getMaxDamage() / 10);

            boolean crystal = false;

            if (VoidClient.switchOn("crystalWarning") && player.isAlive() && player.tickCount % 5 == 0) {

                int radius = 4 + 2 * VoidClient.number("crystal.warningRadius", 4);

                crystal = !client.level.getEntitiesOfClass(net.minecraft.world.entity.boss.enderdragon.EndCrystal.class,

                    player.getBoundingBox().inflate(radius),

                    entity -> player.distanceToSqr(entity) <= radius * radius).isEmpty();

            } else if (VoidClient.switchOn("crystalWarning") && player.isAlive()) {

                crystal = crystalWarning;

            }

            boolean reserve = false;

            if (VoidClient.switchOn("totemReserve") && player.isAlive()) {

                int count = player.getInventory().countItem(net.minecraft.world.item.Items.TOTEM_OF_UNDYING);

                reserve = count <= VoidClient.number("survival.totems", 4);

            }

            var alerts = new java.util.ArrayList<String>();

            if (health && !healthWarning) alerts.add("Low health");

            if (worn && !toolWarning) alerts.add("Tool durability below 10%");

            if (crystal && !crystalWarning) alerts.add("End crystal nearby");

            if (reserve && !reserveWarning) alerts.add("Low totem reserve");

            crystalWarning = crystal;

            reserveWarning = reserve;

            if (!alerts.isEmpty()) {

                String warning = String.join(" • ", alerts);

                player.sendOverlayMessage(Component.literal("Void • " + warning));

                if (VoidClient.number("alerts.sound", 2) == 0)

                    player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 0.7f, 1f);

            }

            healthWarning = health;

            toolWarning = worn;

        }

    }

}




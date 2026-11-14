package dev.voidclient;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

final class ModuleIcons {
    static Item item(String id) {
        return switch (id) {
            case "switch.eat" -> Items.BREAD;
            case "switch.gap" -> Items.ENCHANTED_GOLDEN_APPLE;
            case "switch.replenish" -> Items.CHEST;
            case "switch.walk" -> Items.LEATHER_BOOTS;
            case "switch.jump" -> Items.RABBIT_FOOT;
            case "switch.sneak" -> Items.FEATHER;
            case "switch.autoWeapon" -> Items.IRON_SWORD;
            case "switch.shear" -> Items.SHEARS;
            case "switch.breed" -> Items.WHEAT;
            case "switch.tagMob" -> Items.NAME_TAG;
            case "switch.expThrow" -> Items.EXPERIENCE_BOTTLE;
            case "switch.lookDown" -> Items.ENDER_PEARL;
            case "switch.anchorSync" -> Items.RESPAWN_ANCHOR;
            case "switch.visualPlayers" -> Items.SPYGLASS;
            case "switch.visualBots", "visualBot" -> Items.ARMOR_STAND;
            case "switch.visualItems" -> Items.BUNDLE;
            case "switch.visualLogout" -> Items.OAK_DOOR;
            case "switch.visualXray" -> Items.DIAMOND_ORE;
            case "switch.visualBases" -> Items.END_CRYSTAL;
            case "visualBlock" -> Items.DIAMOND_PICKAXE;
            case "switch.breakGuard" -> Items.IRON_PICKAXE;
            case "anchorCycle" -> Items.FLINT_AND_STEEL;
            case "anchorCharges" -> Items.GLOWSTONE;
            case "switch.surround" -> Items.OBSIDIAN;
            case "switch.holeRadar" -> Items.BEDROCK;
            case "switch.armor" -> Items.DIAMOND_CHESTPLATE;
            case "switch.mend" -> Items.EXPERIENCE_BOTTLE;
            case "damagePreview" -> Items.END_CRYSTAL;
            case "packSpace" -> Items.CHEST;
            case "blockLight" -> Items.LANTERN;
            case "torch" -> Items.TORCH;
            case "switch.sword" -> Items.DIAMOND_SWORD;
            case "switch.crystal" -> Items.END_CRYSTAL;
            case "switch.blastBuffer" -> Items.OBSIDIAN;
            case "switch.crystalWarning" -> Items.AMETHYST_SHARD;
            case "switch.totemReserve" -> Items.GOLD_INGOT;
            case "switch.totem" -> Items.TOTEM_OF_UNDYING;
            case "sprintStatus", "switch.sprint" -> Items.LEATHER_BOOTS;
            case "switch.tool" -> Items.DIAMOND_PICKAXE;
            case "switch.respawn" -> Items.RESPAWN_ANCHOR;
            case "switch.lowHealth" -> Items.GOLDEN_APPLE;
            case "switch.durabilityAlert", "gear" -> Items.ANVIL;
            case "food" -> Items.BREAD;
            case "shield" -> Items.SHIELD;
            case "mark", "waypoint" -> Items.LODESTONE;
            case "clearMark" -> Items.SPONGE;
            case "copy" -> Items.PAPER;
            case "xyz" -> Items.MAP;
            case "heading" -> Items.COMPASS;
            case "fps" -> Items.REPEATER;
            case "portal" -> Items.OBSIDIAN;
            default -> Items.NETHER_STAR;
        };
    }

    static void registerActions(VoidClient registry) {
        registry.action("food","Select food",c -> select(c,true));
        registry.action("shield","Select shield",c -> select(c,false));
        registry.action("clearMark","Clear waypoint",c -> {
            VoidClient.cfg.remove(VoidClient.scope(c));
            VoidClient.save();
            c.player.sendOverlayMessage(net.minecraft.network.chat.Component.literal("Waypoint cleared"));
        });
    }

    private static void select(net.minecraft.client.Minecraft client, boolean food) {
        var inventory = client.player.getInventory();
        for (int i=0; i<9; i++) {
            var stack=inventory.getItem(i);
            if (food ? stack.has(DataComponents.FOOD) : stack.is(Items.SHIELD)) {
                inventory.setSelectedSlot(i);
                return;
            }
        }
        client.player.sendOverlayMessage(net.minecraft.network.chat.Component.literal(food?"No food in hotbar":"No shield in hotbar"));
    }
}


package dev.voidclient;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.Hand;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.item.Items;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.block.BlockState;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

public final class AnchorSync {
    private enum Phase { CHARGE, WAIT_FOR_CHARGE, DETONATE, WAIT_FOR_REMOVAL }
    private static AnchorSync pending;
    private static boolean sendingUse;
    private final ClientWorld level;
    private final BlockPos pos;
    private final int originalSlot;
    private final Object owner;
    private final int deadline;
    private Phase phase;

    private AnchorSync(MinecraftClient c, BlockPos pos, boolean charged) {
        this.level = c.world;
        this.pos = pos.toImmutable();
        this.owner = c.player;
        this.originalSlot = c.player.getInventory().getSelectedSlot();
        this.deadline = c.player.age + 20 * (1 + VoidClient.number("anchor.timeout", 4));
        this.phase = charged ? Phase.DETONATE : Phase.CHARGE;
    }

    static void register(VoidClient registry) {
        Preferences.toggle(registry, "anchorSync", "Anchor Sync");
        registry.action("anchorCycle", "Run Anchor Sync once", AnchorSync::start);
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            var c = MinecraftClient.getInstance();
            if (world != c.world || player != c.player || sendingUse || !VoidClient.switchOn("anchorSync") ||
                hand != Hand.MAIN_HAND || player.isSneaking() || player.isSpectator() ||
                !world.getBlockState(hit.getBlockPos()).isOf(Blocks.RESPAWN_ANCHOR) ||
                c.world.getEnvironmentAttributes().getAttributeValue(EnvironmentAttributes.RESPAWN_ANCHOR_WORKS_GAMEPLAY, hit.getBlockPos())) return net.minecraft.util.ActionResult.PASS;
            if (pending == null) startAt(c, hit);
            return net.minecraft.util.ActionResult.FAIL;
        });
        registry.hud("anchorCharges", "Anchor charges", c -> {
            var hit = aim(c);
            if (hit == null) return null;
            var state = c.world.getBlockState(hit.getBlockPos());
            return state.isOf(Blocks.RESPAWN_ANCHOR) ? "Anchor  " + state.get(RespawnAnchorBlock.CHARGES) + "/4" : null;
        });
        ClientTickEvents.END_CLIENT_TICK.register(c -> { if (pending != null) pending.tick(c); });
    }

    private static BlockHitResult aim(MinecraftClient c) {
        return c.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }

    private static void start(MinecraftClient c) {
        if (c.player == null || c.world == null || c.interactionManager == null || pending != null) return;
        startAt(c, aim(c));
    }

    private static void startAt(MinecraftClient c, BlockHitResult hit) {
        if (hit == null || !c.world.getBlockState(hit.getBlockPos()).isOf(Blocks.RESPAWN_ANCHOR)) {
            SurvivalKit.message(c, "Aim at a respawn anchor"); return;
        }
        if (c.world.getEnvironmentAttributes().getAttributeValue(EnvironmentAttributes.RESPAWN_ANCHOR_WORKS_GAMEPLAY, hit.getBlockPos())) {
            SurvivalKit.message(c, "This anchor sets spawn here"); return;
        }
        if (c.currentScreen instanceof Console) c.setScreen(null);
        pending = new AnchorSync(c, hit.getBlockPos(), c.world.getBlockState(hit.getBlockPos()).get(RespawnAnchorBlock.CHARGES) > 0);
    }

    public static boolean waiting() { return pending != null; }

    public static void blockUpdate(BlockPos pos, BlockState state) {
        var cycle = pending;
        if (cycle == null || !cycle.pos.equals(pos)) return;
        if (!state.isOf(Blocks.RESPAWN_ANCHOR)) { pending = null; return; }
        if (cycle.phase == Phase.WAIT_FOR_CHARGE && state.get(RespawnAnchorBlock.CHARGES) > 0)
            cycle.phase = Phase.DETONATE;
    }

    private void tick(MinecraftClient c) {
        if (c.world != level || c.player == null || c.player != owner || c.interactionManager == null || c.currentScreen != null ||
            !c.player.isAlive() || c.player.isSpectator() || c.player.isSneaking() || c.player.isUsingItem() ||
            c.player.getInventory().getSelectedSlot() != originalSlot) { pending = null; return; }
        if (c.player.age >= deadline) { pending = null; SurvivalKit.message(c, "Anchor Sync timed out"); return; }
        if (phase == Phase.WAIT_FOR_CHARGE || phase == Phase.WAIT_FOR_REMOVAL) return;
        var hit = aim(c);
        if (hit == null || !hit.getBlockPos().equals(pos) || !level.getBlockState(pos).isOf(Blocks.RESPAWN_ANCHOR)) {
            pending = null; return;
        }
        boolean charge = phase == Phase.CHARGE;
        var inv = c.player.getInventory();
        if (!charge && c.player.getOffHandStack().isOf(Items.GLOWSTONE)) {
            pending = null; SurvivalKit.message(c, "Move glowstone out of your offhand to detonate"); return;
        }
        int slot = originalSlot;
        if (inv.getStack(slot).isOf(Items.GLOWSTONE) != charge) {
            for (slot = 0; slot < 9 && inv.getStack(slot).isOf(Items.GLOWSTONE) != charge; slot++) {}
        }
        if (slot == 9) {
            pending = null;
            SurvivalKit.message(c, charge ? "Anchor Sync needs glowstone in the hotbar" : "Anchor Sync needs a non-glowstone slot");
            return;
        }
        phase = charge ? Phase.WAIT_FOR_CHARGE : Phase.WAIT_FOR_REMOVAL;
        inv.setSelectedSlot(slot);
        sendingUse = true;
        try {
            c.interactionManager.interactBlock(c.player, Hand.MAIN_HAND, hit);
        } finally { sendingUse = false; inv.setSelectedSlot(originalSlot); }
    }
}

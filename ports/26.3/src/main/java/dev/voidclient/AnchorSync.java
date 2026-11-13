package dev.voidclient;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class AnchorSync {
    private enum Phase { CHARGE, WAIT_FOR_CHARGE, DETONATE, WAIT_FOR_REMOVAL }
    private static AnchorSync pending;
    private static boolean sendingUse;
    private final ClientLevel level;
    private final BlockPos pos;
    private final int originalSlot;
    private final Object owner;
    private final int deadline;
    private Phase phase;

    private AnchorSync(Minecraft c, BlockPos pos, boolean charged) {
        this.level = c.level;
        this.pos = pos.immutable();
        this.owner = c.player;
        this.originalSlot = c.player.getInventory().getSelectedSlot();
        this.deadline = c.player.tickCount + 20 * (1 + VoidClient.number("anchor.timeout", 4));
        this.phase = charged ? Phase.DETONATE : Phase.CHARGE;
    }

    static void register(VoidClient registry) {
        Preferences.toggle(registry, "anchorSync", "Anchor Sync");
        registry.action("anchorCycle", "Run Anchor Sync once", AnchorSync::start);
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            var c = Minecraft.getInstance();
            if (world != c.level || player != c.player || sendingUse || !VoidClient.switchOn("anchorSync") ||
                hand != InteractionHand.MAIN_HAND || player.isShiftKeyDown() || player.isSpectator() ||
                !world.getBlockState(hit.getBlockPos()).is(Blocks.RESPAWN_ANCHOR) ||
                c.level.environmentAttributes().getValue(EnvironmentAttributes.RESPAWN_ANCHOR_WORKS, hit.getBlockPos())) return net.minecraft.world.InteractionResult.PASS;
            if (pending == null) startAt(c, hit);
            return net.minecraft.world.InteractionResult.FAIL;
        });
        registry.hud("anchorCharges", "Anchor charges", c -> {
            var hit = aim(c);
            if (hit == null) return null;
            var state = c.level.getBlockState(hit.getBlockPos());
            return state.is(Blocks.RESPAWN_ANCHOR) ? "Anchor  " + state.getValue(RespawnAnchorBlock.CHARGE) + "/4" : null;
        });
        ClientTickEvents.END_CLIENT_TICK.register(c -> { if (pending != null) pending.tick(c); });
    }

    private static BlockHitResult aim(Minecraft c) {
        return c.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }

    private static void start(Minecraft c) {
        if (c.player == null || c.level == null || c.gameMode == null || pending != null) return;
        startAt(c, aim(c));
    }

    private static void startAt(Minecraft c, BlockHitResult hit) {
        if (hit == null || !c.level.getBlockState(hit.getBlockPos()).is(Blocks.RESPAWN_ANCHOR)) {
            SurvivalKit.message(c, "Aim at a respawn anchor"); return;
        }
        if (c.level.environmentAttributes().getValue(EnvironmentAttributes.RESPAWN_ANCHOR_WORKS, hit.getBlockPos())) {
            SurvivalKit.message(c, "This anchor sets spawn here"); return;
        }
        if (c.gui.screen() instanceof Console) c.gui.setScreen(null);
        pending = new AnchorSync(c, hit.getBlockPos(), c.level.getBlockState(hit.getBlockPos()).getValue(RespawnAnchorBlock.CHARGE) > 0);
    }

    public static boolean waiting() { return pending != null; }

    public static void blockUpdate(BlockPos pos, BlockState state) {
        var cycle = pending;
        if (cycle == null || !cycle.pos.equals(pos)) return;
        if (!state.is(Blocks.RESPAWN_ANCHOR)) { pending = null; return; }
        if (cycle.phase == Phase.WAIT_FOR_CHARGE && state.getValue(RespawnAnchorBlock.CHARGE) > 0)
            cycle.phase = Phase.DETONATE;
    }

    private void tick(Minecraft c) {
        if (c.level != level || c.player == null || c.player != owner || c.gameMode == null || c.gui.screen() != null ||
            !c.player.isAlive() || c.player.isSpectator() || c.player.isShiftKeyDown() || c.player.isUsingItem() ||
            c.player.getInventory().getSelectedSlot() != originalSlot) { pending = null; return; }
        if (c.player.tickCount >= deadline) { pending = null; SurvivalKit.message(c, "Anchor Sync timed out"); return; }
        if (phase == Phase.WAIT_FOR_CHARGE || phase == Phase.WAIT_FOR_REMOVAL) return;
        var hit = aim(c);
        if (hit == null || !hit.getBlockPos().equals(pos) || !level.getBlockState(pos).is(Blocks.RESPAWN_ANCHOR)) {
            pending = null; return;
        }
        boolean charge = phase == Phase.CHARGE;
        var inv = c.player.getInventory();
        if (!charge && c.player.getOffhandItem().is(Items.GLOWSTONE)) {
            pending = null; SurvivalKit.message(c, "Move glowstone out of your offhand to detonate"); return;
        }
        int slot = originalSlot;
        if (inv.getItem(slot).is(Items.GLOWSTONE) != charge) {
            for (slot = 0; slot < 9 && inv.getItem(slot).is(Items.GLOWSTONE) != charge; slot++) {}
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
            c.gameMode.useItemOn(c.player, InteractionHand.MAIN_HAND, hit);
        } finally { sendingUse = false; inv.setSelectedSlot(originalSlot); }
    }
}

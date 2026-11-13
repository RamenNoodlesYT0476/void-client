package dev.voidclient.mixin;

import dev.voidclient.AnchorSync;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
abstract class AnchorUpdates {
    @Inject(method="handleBlockUpdate", at=@At("TAIL"))
    private void voidBlockUpdate(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
        if (AnchorSync.waiting()) AnchorSync.blockUpdate(packet.getPos(), packet.getBlockState());
    }

    @Inject(method="handleChunkBlocksUpdate", at=@At("TAIL"))
    private void voidSectionUpdate(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
        if (AnchorSync.waiting()) packet.runUpdates(AnchorSync::blockUpdate);
    }
}

package dev.voidclient.mixin;

import dev.voidclient.AnchorSync;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
abstract class AnchorUpdates {
    @Inject(method="onBlockUpdate", at=@At("TAIL"))
    private void voidBlockUpdate(BlockUpdateS2CPacket packet, CallbackInfo ci) {
        if (AnchorSync.waiting()) AnchorSync.blockUpdate(packet.getPos(), packet.getState());
    }

    @Inject(method="onChunkDeltaUpdate", at=@At("TAIL"))
    private void voidSectionUpdate(ChunkDeltaUpdateS2CPacket packet, CallbackInfo ci) {
        if (AnchorSync.waiting()) packet.visitUpdates(AnchorSync::blockUpdate);
    }
    @Inject(method="onEntityStatus", at=@At("TAIL"))
    private void voidTotemPop(net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket packet, CallbackInfo ci){
        var c=net.minecraft.client.MinecraftClient.getInstance();
        if(c.player!=null && packet.getStatus()==35 && packet.getEntity(c.world)==c.player)
            dev.voidclient.SessionTools.recordPop(c.getNetworkHandler());
    }
}

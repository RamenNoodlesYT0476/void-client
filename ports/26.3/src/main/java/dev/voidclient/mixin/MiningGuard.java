package dev.voidclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(net.minecraft.client.multiplayer.MultiPlayerGameMode.class)
abstract class MiningGuard {
    @Inject(method={"startDestroyBlock","continueDestroyBlock"},at=@At("HEAD"),cancellable=true)
    private void voidProtectTool(net.minecraft.core.BlockPos pos, net.minecraft.core.Direction side, CallbackInfoReturnable<Boolean> ci){
        if(dev.voidclient.SessionTools.protectTool())ci.setReturnValue(false);
    }
}

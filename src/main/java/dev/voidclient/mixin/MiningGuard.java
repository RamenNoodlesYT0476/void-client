package dev.voidclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(net.minecraft.client.network.ClientPlayerInteractionManager.class)
abstract class MiningGuard {
    @Inject(method={"attackBlock","updateBlockBreakingProgress"},at=@At("HEAD"),cancellable=true)
    private void voidProtectTool(net.minecraft.util.math.BlockPos pos, net.minecraft.util.math.Direction side, CallbackInfoReturnable<Boolean> ci){
        if(dev.voidclient.SessionTools.protectTool())ci.setReturnValue(false);
    }
}

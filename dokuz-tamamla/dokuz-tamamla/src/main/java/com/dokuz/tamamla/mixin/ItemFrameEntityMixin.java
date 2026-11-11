package com.dokuz.tamamla.mixin;

import com.dokuz.tamamla.DisguiseManager;
import com.dokuz.tamamla.FrameRealStackAccess;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Item frame'de gösterilen item. Sadece istemci tarafındaki (senin ekranındaki) entity'de değişir;
 * sunucudaki gerçek item aynı kalır.
 */
@Mixin(ItemFrameEntity.class)
public abstract class ItemFrameEntityMixin implements FrameRealStackAccess {
    @Shadow @Final private static TrackedData<ItemStack> ITEM_STACK;

    @Override
    public ItemStack dokuz$realStack() {
        return ((ItemFrameEntity) (Object) this).getDataTracker().get(ITEM_STACK);
    }

    @Inject(method = "getHeldItemStack", at = @At("HEAD"), cancellable = true)
    private void dokuz$visual(CallbackInfoReturnable<ItemStack> cir) {
        ItemFrameEntity self = (ItemFrameEntity) (Object) this;
        if (self.getWorld() == null || !self.getWorld().isClient) return;
        ItemStack v = DisguiseManager.INSTANCE.frameVisual(self, self.getDataTracker().get(ITEM_STACK));
        if (v != null) cir.setReturnValue(v);
    }
}

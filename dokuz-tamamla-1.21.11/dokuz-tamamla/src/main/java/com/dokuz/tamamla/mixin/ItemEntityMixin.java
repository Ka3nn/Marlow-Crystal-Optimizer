package com.dokuz.tamamla.mixin;

import com.dokuz.tamamla.DisguiseManager;
import com.dokuz.tamamla.GroundRealStackAccess;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Yere atılan sahte item yerde de sahte görünsün (sadece senin ekranında). */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin implements GroundRealStackAccess {
    @Unique
    private static boolean dokuz$bypass;

    @Override
    public ItemStack dokuz$realStack() {
        dokuz$bypass = true;
        try {
            return ((ItemEntity) (Object) this).getStack();
        } finally {
            dokuz$bypass = false;
        }
    }

    @Inject(method = "getStack", at = @At("HEAD"), cancellable = true)
    private void dokuz$visual(CallbackInfoReturnable<ItemStack> cir) {
        if (dokuz$bypass || !DisguiseManager.INSTANCE.hasGroundDisguises()) return;
        ItemEntity self = (ItemEntity) (Object) this;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null || mc.world.getEntityById(self.getId()) != self) return;
        ItemStack v = DisguiseManager.INSTANCE.groundVisual(self, dokuz$realStack());
        if (v != null) cir.setReturnValue(v);
    }
}

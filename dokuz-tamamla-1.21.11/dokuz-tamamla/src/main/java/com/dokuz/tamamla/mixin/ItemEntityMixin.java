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

/**
 * Yerdeki / dropper'dan uçan item ve alınırken oyuncuya doğru uçan kopyası
 * sahte görünsün (sadece senin ekranında).
 */
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
        if (dokuz$bypass) return;
        DisguiseManager m = DisguiseManager.INSTANCE;
        if (!m.hasGroundDisguises()) return;
        ItemEntity self = (ItemEntity) (Object) this;
        ItemStack real = dokuz$realStack();
        // Alma animasyonundaki kopya (dünyada değildir)
        ItemStack cv = m.copyVisual(self, real);
        if (cv != null) { cir.setReturnValue(cv); return; }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null || mc.world.getEntityById(self.getId()) != self) return;
        ItemStack v = m.groundVisual(self, real);
        if (v != null) cir.setReturnValue(v);
    }

    /** Alma animasyonu için yapılan kopya da aynı görünsün. */
    @Inject(method = "copy", at = @At("RETURN"), require = 0)
    private void dokuz$copied(CallbackInfoReturnable<ItemEntity> cir) {
        DisguiseManager.INSTANCE.onItemCopied((ItemEntity) (Object) this, cir.getReturnValue());
    }
}

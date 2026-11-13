package com.dokuz.tamamla.mixin;

import com.dokuz.tamamla.DisguiseManager;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Elde tutulan item (birinci şahıs). Tek elde ve iki elde tutulan haritanın resmi dahil.
 * Sadece kendi elin etkilenir; başka oyuncuların elindeki itemler değişmez.
 */
@Mixin(HeldItemRenderer.class)
public class HeldItemRendererMixin {
    @ModifyVariable(method = {"renderFirstPersonItem", "renderFirstPersonMap"},
            at = @At("HEAD"), argsOnly = true, require = 0)
    private ItemStack dokuz$swapHeld(ItemStack stack) {
        return DisguiseManager.INSTANCE.heldVisual(stack);
    }
}

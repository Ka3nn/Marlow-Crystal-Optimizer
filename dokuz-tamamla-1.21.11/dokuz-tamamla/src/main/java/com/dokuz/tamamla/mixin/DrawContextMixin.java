package com.dokuz.tamamla.mixin;

import com.dokuz.tamamla.DisguiseManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Hotbar ve envanterdeki ikon. */
@Mixin(DrawContext.class)
public class DrawContextMixin {
    @ModifyVariable(
            method = "drawItem",
            at = @At("HEAD"), argsOnly = true, require = 0)
    private ItemStack dokuz$swap(ItemStack stack) {
        return DisguiseManager.INSTANCE.inventoryVisual(stack);
    }
}

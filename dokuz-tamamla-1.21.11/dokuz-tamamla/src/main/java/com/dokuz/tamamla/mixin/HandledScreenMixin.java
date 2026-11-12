package com.dokuz.tamamla.mixin;

import com.dokuz.tamamla.DisguiseManager;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Envanterde üzerine gelince çıkan yazı (isim, map #). */
@Mixin(HandledScreen.class)
public class HandledScreenMixin {
    @ModifyVariable(method = "drawMouseoverTooltip", at = @At("STORE"), ordinal = 0, require = 0)
    private ItemStack dokuz$swapTooltip(ItemStack stack) {
        return DisguiseManager.INSTANCE.inventoryVisual(stack);
    }
}

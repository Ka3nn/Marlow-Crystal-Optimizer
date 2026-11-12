package com.dokuz.tamamla.mixin;

import com.dokuz.tamamla.DisguiseManager;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Hotbar ikonu ve item seçince üstte çıkan isim. */
@Mixin(InGameHud.class)
public class InGameHudMixin {
    /** Hotbar'daki item ikonu. */
    @ModifyVariable(method = "renderHotbarItem", at = @At("HEAD"), argsOnly = true, require = 0)
    private ItemStack dokuz$hotbarItem(ItemStack stack) {
        return DisguiseManager.INSTANCE.inventoryVisual(stack);
    }

    @Redirect(method = "renderHeldItemTooltip",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;getName()Lnet/minecraft/text/Text;"),
            require = 0)
    private Text dokuz$heldName(ItemStack stack) {
        return DisguiseManager.INSTANCE.inventoryVisual(stack).getName();
    }
}

package com.dokuz.tamamla.mixin;

import com.dokuz.tamamla.DisguiseManager;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Hotbar'da item seçince üstte çıkan isim. */
@Mixin(InGameHud.class)
public class InGameHudMixin {
    @Redirect(method = "renderHeldItemTooltip",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;getName()Lnet/minecraft/text/Text;"),
            require = 0)
    private Text dokuz$heldName(ItemStack stack) {
        return DisguiseManager.INSTANCE.inventoryVisual(stack).getName();
    }
}

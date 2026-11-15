package com.dokuz.tamamla.mixin;

import com.dokuz.tamamla.DisguiseManager;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Envanter / dropper ekranındaki slotlar ve üzerine gelince çıkan yazı. */
@Mixin(HandledScreen.class)
public class HandledScreenMixin {
    /** Çizilen slotu not al. */
    @ModifyVariable(method = "drawSlot", at = @At("HEAD"), argsOnly = true, require = 0)
    private Slot dokuz$captureSlot(Slot slot) {
        DisguiseManager.INSTANCE.currentSlot = slot;
        return slot;
    }

    /** Slotun gösterdiği item (envanterde sahte görünüm, dropper'da sayı ayarı). */
    @ModifyVariable(method = "drawSlot", at = @At("STORE"), ordinal = 0, require = 0)
    private ItemStack dokuz$swapSlot(ItemStack stack) {
        DisguiseManager m = DisguiseManager.INSTANCE;
        return m.slotVisual(m.currentSlot, stack);
    }

    @ModifyVariable(method = "drawMouseoverTooltip", at = @At("STORE"), ordinal = 0, require = 0)
    private ItemStack dokuz$swapTooltip(ItemStack stack) {
        return DisguiseManager.INSTANCE.inventoryVisual(stack);
    }
}

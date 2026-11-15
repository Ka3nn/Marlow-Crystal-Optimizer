package com.dokuz.tamamla.mixin;

import com.dokuz.tamamla.DisguiseManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Envanter / dropper ekranındaki slotlar, üzerine gelince çıkan yazı ve tıklamalar. */
@Mixin(HandledScreen.class)
public class HandledScreenMixin {
    @Shadow protected Slot focusedSlot;

    /** Çizilen slotu not al. */
    @ModifyVariable(method = "drawSlot", at = @At("HEAD"), argsOnly = true, require = 0)
    private Slot dokuz$captureSlot(Slot slot) {
        DisguiseManager.INSTANCE.currentSlot = slot;
        return slot;
    }

    /** Slotun gösterdiği item (envanterde sahte görünüm, dropper'da ilk dizilişe göre görünüm). */
    @ModifyVariable(method = "drawSlot", at = @At("STORE"), ordinal = 0, require = 0)
    private ItemStack dokuz$swapSlot(ItemStack stack) {
        DisguiseManager m = DisguiseManager.INSTANCE;
        return m.slotVisual(m.currentSlot, stack);
    }

    /** Ekranda boş görünen dropper slotunun üzerine gelince yazı çıkmasın. */
    @Inject(method = "drawMouseoverTooltip", at = @At("HEAD"), cancellable = true, require = 0)
    private void dokuz$hideEmptyTooltip(DrawContext context, int x, int y, CallbackInfo ci) {
        if (focusedSlot == null || !focusedSlot.hasStack()) return;
        if (DisguiseManager.INSTANCE.slotVisual(focusedSlot, focusedSlot.getStack()).isEmpty()) ci.cancel();
    }

    /** Üzerine gelince çıkan yazı da ekranda görünen itemi anlatsın. */
    @ModifyVariable(method = "drawMouseoverTooltip", at = @At("STORE"), ordinal = 0, require = 0)
    private ItemStack dokuz$swapTooltip(ItemStack stack) {
        return DisguiseManager.INSTANCE.slotVisual(focusedSlot, stack);
    }

    /** Dropper'da tıklanan slot: ekranda görünen yer → gerçek yer. */
    @ModifyVariable(method = "onMouseClick(Lnet/minecraft/screen/slot/Slot;IILnet/minecraft/screen/slot/SlotActionType;)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private int dokuz$remapSlotId(int slotId) {
        return DisguiseManager.INSTANCE.remapContainerClick(slotId);
    }

    @ModifyVariable(method = "onMouseClick(Lnet/minecraft/screen/slot/Slot;IILnet/minecraft/screen/slot/SlotActionType;)V",
            at = @At("HEAD"), argsOnly = true, require = 0)
    private Slot dokuz$remapSlot(Slot slot) {
        if (slot == null) return null;
        int mapped = DisguiseManager.INSTANCE.remapContainerClick(slot.id);
        if (mapped == slot.id) return slot;
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player == null ? slot : mc.player.currentScreenHandler.getSlot(mapped);
    }
}

package com.dokuz.tamamla;

import net.minecraft.item.ItemStack;

/** Item frame'deki GERÇEK itemi (sahte görünüm uygulanmadan) okumak için. */
public interface FrameRealStackAccess {
    ItemStack dokuz$realStack();
}

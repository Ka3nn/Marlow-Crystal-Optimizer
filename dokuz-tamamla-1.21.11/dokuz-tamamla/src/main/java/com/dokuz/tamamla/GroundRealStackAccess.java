package com.dokuz.tamamla;

import net.minecraft.item.ItemStack;

/** Yerdeki itemin GERÇEK halini (sahte görünüm uygulanmadan) okumak için. */
public interface GroundRealStackAccess {
    ItemStack dokuz$realStack();
}

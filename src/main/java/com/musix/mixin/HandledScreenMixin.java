package com.musix.mixin;

import com.musix.MusixClient;
import com.musix.config.MusixConfig;
import com.musix.config.MusixStatus;
import com.musix.key.KeyBindings;
import com.musix.util.DebugChat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HandledScreen.class)
public class HandledScreenMixin {

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void musix$preKeyPressed(int keyCode, int scanCode, int modifiers,
                                     CallbackInfoReturnable<Boolean> cir) {
        Object self = this;
        if (!(self instanceof GenericContainerScreen gcs)) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.interactionManager == null) return;

        MusixConfig cfg = MusixClient.config();
        if (cfg == null) return;
        Text title = gcs.getTitle();
        if (title == null || !cfg.titleMatchesPrefix(title.getString())) return;

        String preset = cfg.activePresetForTitle(title.getString());

        if (cfg.debugMode) DebugChat.info("[Mixin] preset=" + preset + " key=" + keyCode + " mods=" + modifiers);

        for (KeyBindings.NoteEntry note : KeyBindings.getNotes(preset)) {
            if (!note.matches(keyCode, scanCode, modifiers)) continue;

            GenericContainerScreenHandler handler = gcs.getScreenHandler();
            int slot = note.mapping().slot;
            if (slot < 0 || slot >= handler.slots.size()) {
                cir.setReturnValue(true);
                return;
            }
            ItemStack stack = handler.slots.get(slot).getStack();
            client.interactionManager.clickSlot(handler.syncId, slot, cfg.clickButton,
                    parseAction(cfg.clickAction), client.player);
            MusixStatus.recordNote(note.mapping().note, slot);
            if (cfg.debugMode) {
                String si = stack.isEmpty() ? "빈슬롯" : (stack.getName().getString() + "x" + stack.getCount());
                DebugChat.ok("[Mixin] " + note.mapping().note + " slot=" + slot + " (" + si + ")");
            }
            cir.setReturnValue(true);
            return;
        }
        if (cfg.debugMode) DebugChat.warn("[Mixin] 매칭 없음 (preset=" + preset + ")");
    }

    private static SlotActionType parseAction(String name) {
        if (name == null) return SlotActionType.PICKUP;
        return switch (name) {
            case "QUICK_MOVE" -> SlotActionType.QUICK_MOVE;
            case "SWAP"       -> SlotActionType.SWAP;
            case "CLONE"      -> SlotActionType.CLONE;
            case "THROW"      -> SlotActionType.THROW;
            case "PICKUP_ALL" -> SlotActionType.PICKUP_ALL;
            default           -> SlotActionType.PICKUP;
        };
    }
}

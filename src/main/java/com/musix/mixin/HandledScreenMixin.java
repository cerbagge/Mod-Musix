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
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HandledScreen.class)
public class HandledScreenMixin {

    // v4.1.1: HandledScreen 에 keyReleased 가 override 되어있지 않아 Mixin inject 실패 → 크래시.
    // ScreenKeyboardEvents.allowKeyRelease (KeyHandler.registerEvents 에 등록) 만 사용.
    // 키 자동반복 reset 은 거기서 처리됨.

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

        // v3.12.0: Space 조합 augment (v5.3.0: 조합키 10종으로 확장)
        int effectiveMods = KeyBindings.augmentMods(modifiers);

        if (cfg.debugMode) DebugChat.info("[Mixin] preset=" + preset + " key=" + keyCode + " mods=" + effectiveMods
                + (effectiveMods != modifiers ? "(+조합키)" : ""));

        for (KeyBindings.NoteEntry note : KeyBindings.getNotes(preset)) {
            if (!note.matches(keyCode, scanCode, effectiveMods)) continue;

            // v4.0.3: 키 자동반복 방지 — 처음 누름만 클릭, 꾹 누른 동안 추가 클릭 무시
            if (!KeyBindings.acquireKeyPress(keyCode)) {
                cir.setReturnValue(true); // 처리됨으로 표시 (다른 핸들러 차단)
                return;
            }

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
        // v3.11.1: 매핑 없는 modifier 조합 (Shift+E 등) 은 차단 — 인벤토리 닫힘 방지.
        // 일반 키 (modifier 없음) 는 마크 기본 동작 통과 (채팅 T, 명령어 / 등 사용 가능).
        // ESC 도 통과 (상자 닫기).
        int relevant = modifiers & KeyBindings.MOD_MASK;
        if (relevant != 0 && keyCode != GLFW.GLFW_KEY_ESCAPE) {
            if (cfg.debugMode) DebugChat.warn("[Mixin] 매핑 없는 modifier 조합 차단 key=" + keyCode + " mods=" + modifiers);
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

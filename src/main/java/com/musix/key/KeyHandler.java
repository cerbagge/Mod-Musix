package com.musix.key;

import com.musix.MusixClient;
import com.musix.config.LastSeenContainer;
import com.musix.config.MusixConfig;
import com.musix.config.MusixStatus;
import com.musix.gui.MusixMenuScreen;
import com.musix.util.DebugChat;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public final class KeyHandler {
    private KeyHandler() {}

    private static Screen lastDumpedScreen = null;
    private static int dumpDelayTicks = 0;

    public static void registerEvents() {
        ClientTickEvents.END_CLIENT_TICK.register(KeyHandler::onTick);
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof GenericContainerScreen gcs) {
                ScreenKeyboardEvents.allowKeyPress(screen).register((scr, key, scancode, modifiers) ->
                        !handleScreenKey(client, gcs, key, scancode, modifiers));
                if (screen != lastDumpedScreen) {
                    dumpDelayTicks = 10;
                    lastDumpedScreen = screen;
                }
            }
        });
    }

    private static void onTick(MinecraftClient client) {
        KeyBinding menu = KeyBindings.getMenuBinding();
        if (menu != null) {
            while (menu.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new MusixMenuScreen(null));
                }
            }
        }
        if (dumpDelayTicks > 0) {
            dumpDelayTicks--;
            if (dumpDelayTicks == 0
                    && client.currentScreen instanceof GenericContainerScreen gcs
                    && MusixClient.config() != null) {
                dumpContainerSlots(gcs, MusixClient.config());
            }
        }
    }

    private static boolean handleScreenKey(MinecraftClient client, GenericContainerScreen screen,
                                           int key, int scancode, int modifiers) {
        if (client.player == null || client.interactionManager == null) return false;
        MusixConfig cfg = MusixClient.config();
        Text title = screen.getTitle();
        if (title == null || !cfg.titleMatchesPrefix(title.getString())) return false;

        String preset = cfg.activePresetForTitle(title.getString());

        if (cfg.debugMode) DebugChat.info("[KeyHandler] preset=" + preset + " key=" + key + " mods=" + modifiers);

        GenericContainerScreenHandler handler = screen.getScreenHandler();
        int syncId = handler.syncId;
        int slotsCount = handler.slots.size();

        for (KeyBindings.NoteEntry note : KeyBindings.getNotes(preset)) {
            if (note.matches(key, scancode, modifiers)) {
                int slot = note.mapping().slot;
                if (slot < 0 || slot >= slotsCount) return true;
                ItemStack stack = handler.slots.get(slot).getStack();
                client.interactionManager.clickSlot(syncId, slot, cfg.clickButton,
                        parseAction(cfg.clickAction), client.player);
                MusixStatus.recordNote(note.mapping().note, slot);
                if (cfg.debugMode) {
                    String si = stack.isEmpty() ? "빈슬롯" : (stack.getName().getString() + "x" + stack.getCount());
                    DebugChat.ok("[KeyHandler] " + note.mapping().note + " slot=" + slot + " (" + si + ")");
                }
                return true;
            }
        }
        if (cfg.debugMode) DebugChat.warn("[KeyHandler] 매칭 없음 (preset=" + preset + ")");
        return false;
    }

    private static void dumpContainerSlots(GenericContainerScreen gcs, MusixConfig cfg) {
        Text title = gcs.getTitle();
        if (title == null || !cfg.titleMatchesPrefix(title.getString())) return;

        GenericContainerScreenHandler handler = gcs.getScreenHandler();
        int containerRows = handler.getRows();
        int containerSize = containerRows * 9;

        List<Integer> nonEmpty = new ArrayList<>();
        for (int i = 0; i < containerSize && i < handler.slots.size(); i++) {
            if (!handler.slots.get(i).getStack().isEmpty()) nonEmpty.add(i);
        }
        LastSeenContainer.update(title.getString(), containerRows, nonEmpty);

        if (!cfg.debugMode) return;
        String preset = cfg.activePresetForTitle(title.getString());
        DebugChat.info("[상자열림] '" + title.getString() + "' preset=" + preset
                + " rows=" + containerRows + " 비어있지않은=" + nonEmpty.size());
        for (int row = 0; row < containerRows; row++) {
            StringBuilder rowSb = new StringBuilder();
            for (int col = 0; col < 9; col++) {
                int idx = row * 9 + col;
                if (idx >= handler.slots.size()) break;
                if (!handler.slots.get(idx).getStack().isEmpty()) {
                    if (rowSb.length() > 0) rowSb.append(",");
                    rowSb.append(idx);
                }
            }
            if (rowSb.length() > 0) DebugChat.ok("  행" + row + ": " + rowSb);
        }
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

package com.musix.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class DebugChat {
    private DebugChat() {}

    public static void info(String msg) {
        send(msg, Formatting.GRAY);
    }

    public static void ok(String msg) {
        send(msg, Formatting.GREEN);
    }

    public static void warn(String msg) {
        send(msg, Formatting.YELLOW);
    }

    /** v5.5.1: 보안 경고 — 디버그 모드와 무관하게 항상 보여야 하는 메시지용. */
    public static void alert(String msg) {
        send(msg, Formatting.RED);
    }

    private static void send(String msg, Formatting color) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        mc.player.sendMessage(Text.literal("[Musix] " + msg).formatted(color), false);
    }
}

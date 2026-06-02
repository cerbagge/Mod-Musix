package com.musix.gui;

import com.musix.MusixClient;
import com.musix.config.MusixConfig;
import com.musix.input.MusixMidi;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import javax.sound.midi.MidiDevice;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * v4.0.0: MIDI 입력 설정 화면.
 * - 사용 가능한 MIDI 장치 목록
 * - 클릭으로 연결 / 연결 해제
 * - MIDI 입력 활성화 토글
 * - 현재 연결 상태 표시
 */
public class MusixMidiScreen extends Screen {
    private static final int COLOR_TITLE   = 0xFFFFFFFF;
    private static final int COLOR_HEADER  = 0xFF88CCFF;
    private static final int COLOR_LABEL   = 0xFFFFAA00;
    private static final int COLOR_VALUE   = 0xFFFFFFFF;
    private static final int COLOR_DIM     = 0xFFAAAAAA;
    private static final int COLOR_OK      = 0xFF55FF55;
    private static final int COLOR_WARN    = 0xFFFF5555;
    private static final int COLOR_BG      = 0x88222222;
    private static final int COLOR_BORDER  = 0x88888888;
    private static final int ROW_HEIGHT    = 14;

    private final Screen parent;
    private volatile List<MidiDevice.Info> devices = java.util.Collections.emptyList();
    private int listX, listY, listW, listH;
    private int rowYEnable;

    private volatile String flashMessage = null;
    private volatile long flashUntil = 0;
    private volatile boolean flashSuccess = true;

    /** v4.0.1: 비동기 작업 중 (true) 일 때 추가 클릭 차단. */
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private volatile String busyMessage = null;

    public MusixMidiScreen(Screen parent) {
        super(Text.literal("Musix MIDI 입력"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // 초기 장치 스캔도 비동기 — 화면 열 때 hang 방지
        scanAsync();

        int by = this.height - 28;
        int btnW = 110, gap = 6;
        int totalW = btnW * 3 + gap * 2;
        int startX = (this.width - totalW) / 2;

        this.addDrawableChild(ButtonWidget.builder(Text.literal("새로고침"),
                btn -> scanAsync()
        ).dimensions(startX, by, btnW, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("← 고급 설정으로"),
                btn -> { if (this.client != null) this.client.setScreen(this.parent); }
        ).dimensions(startX + btnW + gap, by, btnW, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("닫기"),
                btn -> this.close()
        ).dimensions(startX + (btnW + gap) * 2, by, btnW, 20).build());
    }

    /** v4.0.1: 별도 스레드에서 MIDI 장치 스캔. 결과는 메인 스레드에서 반영. */
    private void scanAsync() {
        if (!busy.compareAndSet(false, true)) return;
        busyMessage = "장치 검색 중...";
        new Thread(() -> {
            try {
                List<MidiDevice.Info> found = MusixMidi.scanInputDevices();
                MinecraftClient.getInstance().execute(() -> {
                    devices = found;
                    busy.set(false);
                    busyMessage = null;
                    flash("✓ 장치 검색 완료 (" + found.size() + "개)", true);
                });
            } catch (Throwable t) {
                MinecraftClient.getInstance().execute(() -> {
                    busy.set(false);
                    busyMessage = null;
                    flash("✗ 검색 실패: " + t.getMessage(), false);
                });
            }
        }, "musix-midi-scan").start();
    }

    /** v4.0.1: 별도 스레드에서 MIDI 연결. 메인 스레드 차단 방지. */
    private void connectAsync(MidiDevice.Info info) {
        if (!busy.compareAndSet(false, true)) {
            flash("⏳ 작업 중 — 잠시 후 다시 시도", false);
            return;
        }
        busyMessage = "연결 중: " + info.getName();
        new Thread(() -> {
            boolean ok;
            try {
                ok = MusixMidi.connect(info.getName());
            } catch (Throwable t) {
                ok = false;
            }
            final boolean okFinal = ok;
            MinecraftClient.getInstance().execute(() -> {
                busy.set(false);
                busyMessage = null;
                if (okFinal) {
                    MusixClient.config().setMidiDeviceName(info.getName());
                    if (!MusixClient.config().midiEnabled) {
                        MusixClient.config().setMidiEnabled(true);
                    }
                    flash("✓ 연결: " + info.getName(), true);
                } else {
                    flash("✗ 연결 실패: " + info.getName(), false);
                }
            });
        }, "musix-midi-connect").start();
    }

    /** v4.0.1: 별도 스레드에서 MIDI 연결 해제. */
    private void disconnectAsync() {
        if (!busy.compareAndSet(false, true)) {
            flash("⏳ 작업 중 — 잠시 후 다시 시도", false);
            return;
        }
        busyMessage = "연결 해제 중...";
        new Thread(() -> {
            try { MusixMidi.disconnect(); } catch (Throwable ignored) {}
            MinecraftClient.getInstance().execute(() -> {
                busy.set(false);
                busyMessage = null;
                MusixClient.config().setMidiDeviceName("");
                flash("✓ 연결 해제", true);
            });
        }, "musix-midi-disconnect").start();
    }

    private void flash(String msg, boolean ok) {
        flashMessage = msg;
        flashSuccess = ok;
        flashUntil = System.currentTimeMillis() + 4000L;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // v5.0.0: 1.20.2+ 는 super.render 가 배경+위젯 / 1.20.1 은 renderBackground 후 super.render
        //? if >=1.20.2 {
        super.render(context, mouseX, mouseY, delta);
        //?} else {
        /*this.renderBackground(context);
        super.render(context, mouseX, mouseY, delta);*/
        //?}
        TextRenderer tr = this.textRenderer;
        int cx = this.width / 2;

        context.drawCenteredTextWithShadow(tr, "♪ Musix MIDI 입력 ♪", cx, 12, COLOR_TITLE);
        context.drawCenteredTextWithShadow(tr,
                "MIDI 키보드/컨트롤러로 노트를 입력하면 자동으로 슬롯이 클릭됩니다",
                cx, 24, COLOR_DIM);

        MusixConfig cfg = MusixClient.config();

        // 토글 박스
        int topY = 44;
        int topX = 40, topW = this.width - 80, topH = 38;
        context.fill(topX, topY, topX + topW, topY + topH, COLOR_BG);
        drawBorder(context, topX, topY, topW, topH);

        rowYEnable = topY + 8;
        context.drawTextWithShadow(tr, "MIDI 입력:", topX + 10, rowYEnable, COLOR_LABEL);
        int enColor = cfg.midiEnabled ? COLOR_OK : COLOR_VERSION();
        context.drawTextWithShadow(tr, (cfg.midiEnabled ? "ON" : "OFF") + "  [클릭으로 토글]",
                topX + 110, rowYEnable, enColor);

        context.drawTextWithShadow(tr, "연결됨:", topX + 10, rowYEnable + 14, COLOR_LABEL);
        String connName = MusixMidi.connectedDeviceName();
        if (connName != null && MusixMidi.isConnected()) {
            context.drawTextWithShadow(tr, connName, topX + 110, rowYEnable + 14, COLOR_OK);
        } else {
            context.drawTextWithShadow(tr, "(연결된 장치 없음)", topX + 110, rowYEnable + 14, COLOR_DIM);
        }

        // 장치 목록 박스
        listX = 40;
        listY = topY + topH + 6;
        listW = this.width - 80;
        listH = this.height - listY - 38;

        context.fill(listX, listY, listX + listW, listY + listH, COLOR_BG);
        drawBorder(context, listX, listY, listW, listH);

        context.drawTextWithShadow(tr, "▣ 사용 가능한 MIDI 장치 (" + devices.size() + ")",
                listX + 8, listY + 6, COLOR_HEADER);

        if (devices.isEmpty()) {
            context.drawTextWithShadow(tr,
                    "MIDI 장치가 감지되지 않았습니다.",
                    listX + 8, listY + 22, COLOR_DIM);
            context.drawTextWithShadow(tr,
                    "MIDI 키보드/컨트롤러를 연결 후 [새로고침] 클릭.",
                    listX + 8, listY + 34, COLOR_DIM);
            context.drawTextWithShadow(tr,
                    "가상 MIDI (loopMIDI 등) 도 사용 가능합니다.",
                    listX + 8, listY + 46, COLOR_DIM);
        } else {
            int dy = listY + 22;
            for (int i = 0; i < devices.size(); i++) {
                MidiDevice.Info info = devices.get(i);
                int y = dy + i * ROW_HEIGHT;
                if (y > listY + listH - ROW_HEIGHT) break;
                boolean isConn = MusixMidi.isConnected()
                        && info.getName().equals(MusixMidi.connectedDeviceName());
                boolean hover = mouseY >= y - 1 && mouseY < y + ROW_HEIGHT - 1
                        && mouseX >= listX + 4 && mouseX < listX + listW - 4;
                if (hover) context.fill(listX + 4, y - 1, listX + listW - 4, y + ROW_HEIGHT - 1, 0x33FFFFFF);

                String name = info.getName();
                if (name.length() > 50) name = name.substring(0, 47) + "...";
                int nameColor = isConn ? COLOR_OK : COLOR_VALUE;
                context.drawTextWithShadow(tr, name, listX + 10, y + 2, nameColor);

                String vendor = info.getVendor();
                if (vendor != null && !vendor.isEmpty() && vendor.length() < 30) {
                    context.drawTextWithShadow(tr, vendor, listX + 240, y + 2, COLOR_DIM);
                }

                String action = isConn ? "[연결 해제]" : "[연결]";
                int actColor = isConn ? COLOR_WARN : COLOR_OK;
                context.drawTextWithShadow(tr, action, listX + listW - 90, y + 2, actColor);
            }
        }

        // 비동기 작업 진행 중 표시 (busy 우선)
        if (busy.get() && busyMessage != null) {
            context.drawCenteredTextWithShadow(tr, "⏳ " + busyMessage, cx, this.height - 42,
                    0xFFFFFF55);
        } else if (flashMessage != null && System.currentTimeMillis() < flashUntil) {
            context.drawCenteredTextWithShadow(tr, flashMessage, cx, this.height - 42,
                    flashSuccess ? COLOR_OK : COLOR_WARN);
        }
    }

    private int COLOR_VERSION() { return 0xFFAAAAAA; }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;
        // 비동기 작업 진행 중에는 추가 클릭 거부 (중복 연결 시도 방지)
        if (busy.get()) {
            flash("⏳ 작업 중 — 잠시 기다려 주세요", false);
            return true;
        }

        // 활성화 토글 (DB 만 쓰는 동기 작업이라 그대로 OK)
        if (mouseY >= rowYEnable && mouseY < rowYEnable + 11
                && mouseX >= 40 && mouseX < this.width - 40) {
            MusixConfig cfg = MusixClient.config();
            cfg.setMidiEnabled(!cfg.midiEnabled);
            flash("MIDI 입력 " + (cfg.midiEnabled ? "활성화" : "비활성화"), true);
            return true;
        }

        // 장치 목록 클릭 → 비동기 connect/disconnect
        int dy = listY + 22;
        for (int i = 0; i < devices.size(); i++) {
            MidiDevice.Info info = devices.get(i);
            int y = dy + i * ROW_HEIGHT;
            if (mouseY < y - 1 || mouseY >= y + ROW_HEIGHT - 1) continue;
            int rx = listX + listW - 90;
            if (mouseX < rx || mouseX >= listX + listW - 4) continue;
            boolean isConn = MusixMidi.isConnected()
                    && info.getName().equals(MusixMidi.connectedDeviceName());
            if (isConn) {
                disconnectAsync();
            } else {
                connectAsync(info);
            }
            return true;
        }
        return false;
    }

    private void drawBorder(DrawContext context, int x, int y, int w, int h) {
        context.fill(x,         y,         x + w,     y + 1,     COLOR_BORDER);
        context.fill(x,         y + h - 1, x + w,     y + h,     COLOR_BORDER);
        context.fill(x,         y,         x + 1,     y + h,     COLOR_BORDER);
        context.fill(x + w - 1, y,         x + w,     y + h,     COLOR_BORDER);
    }

    @Override
    public void close() {
        if (this.client != null) this.client.setScreen(this.parent);
    }

    @Override
    public boolean shouldPause() { return false; }
}

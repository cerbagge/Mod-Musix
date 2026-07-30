package com.musix.gui;

import com.musix.MusixClient;
import com.musix.config.MusixConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

/** v3.6.0 ~ : 일반 유저에게 필요 없는 고급 설정 (디버그 모드 등). */
public class MusixAdvancedScreen extends Screen {
    private static final int COLOR_TITLE     = 0xFFFFFFFF;
    private static final int COLOR_VERSION   = 0xFFAAAAAA;
    private static final int COLOR_LABEL     = 0xFFFFAA00;
    private static final int COLOR_VALUE     = 0xFFFFFFFF;
    private static final int COLOR_OK        = 0xFF55FF55;
    private static final int COLOR_HEADER    = 0xFF88CCFF;
    private static final int COLOR_BG        = 0x88222222;
    private static final int COLOR_BORDER    = 0x88888888;
    private static final int ROW_HEIGHT      = 11;

    private final Screen parent;
    private int rowYDebug;
    private int rowYAutoMap;
    private int rowYOctaveFold;  // v5.2.0
    private int rowYAutoVolume;  // v5.6.0

    public MusixAdvancedScreen(Screen parent) {
        super(Text.literal("Musix 고급 설정"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int by = this.height - 28;
        int btnW = 90, gap = 5;
        int totalW = btnW * 5 + gap * 4;
        int startX = (this.width - totalW) / 2;

        this.addDrawableChild(ButtonWidget.builder(Text.literal("매핑 슬롯"),
                btn -> { if (this.client != null) this.client.setScreen(new MusixSlotsScreen(this)); }
        ).dimensions(startX, by, btnW, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("MIDI"),
                btn -> { if (this.client != null) this.client.setScreen(new MusixMidiScreen(this)); }
        ).dimensions(startX + (btnW + gap), by, btnW, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("설정 폴더"),
                //? if >=1.20.2 {
                btn -> Util.getOperatingSystem().open(FabricLoader.getInstance().getConfigDir().toUri())
                //?} else
                /*btn -> Util.getOperatingSystem().open(FabricLoader.getInstance().getConfigDir().toFile())*/
        ).dimensions(startX + (btnW + gap) * 2, by, btnW, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("← 메뉴"),
                btn -> { if (this.client != null) this.client.setScreen(this.parent); }
        ).dimensions(startX + (btnW + gap) * 3, by, btnW, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("닫기"),
                btn -> this.close()
        ).dimensions(startX + (btnW + gap) * 4, by, btnW, 20).build());
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

        context.drawCenteredTextWithShadow(tr, "♪ Musix 고급 설정 ♪", cx, 12, COLOR_TITLE);
        context.drawCenteredTextWithShadow(tr, "v" + MusixClient.version(), cx, 24, COLOR_VERSION);

        MusixConfig cfg = MusixClient.config();
        int boxY = 44, boxX = 40;
        int boxW = this.width - 80, boxH = 98; // v5.6.0: 음량 고정 행 추가로 +12
        context.fill(boxX, boxY, boxX + boxW, boxY + boxH, COLOR_BG);
        drawBorder(context, boxX, boxY, boxW, boxH);

        int y = boxY + 6;
        context.drawTextWithShadow(tr, "▣ 고급 설정", boxX + 8, y, COLOR_HEADER);
        y += 14;

        rowYDebug = y;
        context.drawTextWithShadow(tr, "디버그 모드:", boxX + 14, y, COLOR_LABEL);
        int dbgColor = cfg.debugMode ? COLOR_OK : COLOR_VERSION;
        context.drawTextWithShadow(tr, (cfg.debugMode ? "ON" : "OFF") + "  [클릭으로 토글]",
                boxX + 130, y, dbgColor);
        y += 12;

        rowYAutoMap = y;
        context.drawTextWithShadow(tr, "상자 열때 자동매핑:", boxX + 14, y, COLOR_LABEL);
        int amColor = cfg.autoMapOnOpen ? COLOR_OK : COLOR_VERSION;
        context.drawTextWithShadow(tr, (cfg.autoMapOnOpen ? "ON" : "OFF") + "  [클릭으로 토글]",
                boxX + 130, y, amColor);
        y += 12;

        rowYOctaveFold = y;
        context.drawTextWithShadow(tr, "범위밖 옥타브 접기:", boxX + 14, y, COLOR_LABEL);
        int ofColor = cfg.octaveFold ? COLOR_OK : COLOR_VERSION;
        context.drawTextWithShadow(tr, (cfg.octaveFold ? "ON" : "OFF") + "  [클릭으로 토글]",
                boxX + 130, y, ofColor);
        y += 12;

        // v5.6.0: 상자 열 때 음량 자동 고정
        rowYAutoVolume = y;
        context.drawTextWithShadow(tr, "상자 열때 음량 " + MusixConfig.VOLUME_DEFAULT + " 고정:",
                boxX + 14, y, COLOR_LABEL);
        int avColor = cfg.autoVolumeOnOpen ? COLOR_OK : COLOR_VERSION;
        context.drawTextWithShadow(tr, (cfg.autoVolumeOnOpen ? "ON" : "OFF") + "  [클릭으로 토글]",
                boxX + 130, y, avColor);
        y += 12;

        context.drawTextWithShadow(tr, "상자 접두사:", boxX + 14, y, COLOR_LABEL);
        context.drawTextWithShadow(tr, "\"" + (cfg.containerPrefix == null ? "" : cfg.containerPrefix) + "\"",
                boxX + 130, y, COLOR_VALUE);
        y += 12;

        // v4.1.4: 키 매핑 내보내기 행 제거 — 매핑 슬롯으로 대체

        context.drawCenteredTextWithShadow(tr,
                "키 매핑 export/import 는 [매핑 슬롯] 화면을 사용하세요.",
                cx, this.height - 48, COLOR_VERSION);
    }

    private void drawBorder(DrawContext context, int x, int y, int w, int h) {
        context.fill(x,         y,         x + w,     y + 1,     COLOR_BORDER);
        context.fill(x,         y + h - 1, x + w,     y + h,     COLOR_BORDER);
        context.fill(x,         y,         x + 1,     y + h,     COLOR_BORDER);
        context.fill(x + w - 1, y,         x + w,     y + h,     COLOR_BORDER);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;
        if (mouseY >= rowYDebug && mouseY < rowYDebug + ROW_HEIGHT) {
            MusixConfig cfg = MusixClient.config();
            cfg.setDebugMode(!cfg.debugMode);
            return true;
        }
        if (mouseY >= rowYAutoMap && mouseY < rowYAutoMap + ROW_HEIGHT) {
            MusixConfig cfg = MusixClient.config();
            cfg.setAutoMapOnOpen(!cfg.autoMapOnOpen);
            return true;
        }
        if (mouseY >= rowYOctaveFold && mouseY < rowYOctaveFold + ROW_HEIGHT) {
            MusixConfig cfg = MusixClient.config();
            cfg.setOctaveFold(!cfg.octaveFold);
            return true;
        }
        if (mouseY >= rowYAutoVolume && mouseY < rowYAutoVolume + ROW_HEIGHT) {
            MusixConfig cfg = MusixClient.config();
            cfg.setAutoVolumeOnOpen(!cfg.autoVolumeOnOpen);
            return true;
        }
        return false;
    }

    @Override
    public void close() {
        if (this.client != null) this.client.setScreen(this.parent);
    }

    @Override
    public boolean shouldPause() { return false; }
}

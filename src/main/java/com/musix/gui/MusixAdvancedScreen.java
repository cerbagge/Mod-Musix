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

/** v3.6.0: 일반 유저에게 필요 없는 고급 설정 (디버그 모드 등). */
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

    public MusixAdvancedScreen(Screen parent) {
        super(Text.literal("Musix 고급 설정"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int by = this.height - 28;
        int btnW = 120, gap = 6;
        int totalW = btnW * 3 + gap * 2;
        int startX = (this.width - totalW) / 2;

        this.addDrawableChild(ButtonWidget.builder(Text.literal("설정 폴더 열기"),
                btn -> Util.getOperatingSystem().open(FabricLoader.getInstance().getConfigDir().toFile())
        ).dimensions(startX, by, btnW, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("← 메뉴로"),
                btn -> { if (this.client != null) this.client.setScreen(this.parent); }
        ).dimensions(startX + btnW + gap, by, btnW, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("닫기"),
                btn -> this.close()
        ).dimensions(startX + (btnW + gap) * 2, by, btnW, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        TextRenderer tr = this.textRenderer;
        int cx = this.width / 2;

        context.drawCenteredTextWithShadow(tr, "♪ Musix 고급 설정 ♪", cx, 12, COLOR_TITLE);
        context.drawCenteredTextWithShadow(tr, "v" + MusixClient.version(), cx, 24, COLOR_VERSION);

        MusixConfig cfg = MusixClient.config();
        int boxY = 44, boxX = 40;
        int boxW = this.width - 80, boxH = 70;
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

        context.drawTextWithShadow(tr, "상자 접두사:", boxX + 14, y, COLOR_LABEL);
        context.drawTextWithShadow(tr, "\"" + (cfg.containerPrefix == null ? "" : cfg.containerPrefix) + "\"",
                boxX + 130, y, COLOR_VALUE);
        y += 12;

        context.drawCenteredTextWithShadow(tr,
                "상자 접두사 편집은 현재 DB 직접 편집 (config/musix-data.mv.db). 향후 메뉴 편집 예정.",
                cx, this.height - 48, COLOR_VERSION);

        super.render(context, mouseX, mouseY, delta);
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
        return false;
    }

    @Override
    public void close() {
        if (this.client != null) this.client.setScreen(this.parent);
    }

    @Override
    public boolean shouldPause() { return false; }
}

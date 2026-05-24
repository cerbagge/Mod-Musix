package com.musix.gui;

import com.musix.config.MusixStatus;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * v4.1.0: Musix 통계 화면 — 세션 정보 + 꺾은선(분당 입력) + TOP 5 + Preset 비율.
 */
public class MusixStatsScreen extends Screen {
    private static final int COLOR_TITLE   = 0xFFFFFFFF;
    private static final int COLOR_HEADER  = 0xFF88CCFF;
    private static final int COLOR_LABEL   = 0xFFFFAA00;
    private static final int COLOR_VALUE   = 0xFFFFFFFF;
    private static final int COLOR_DIM     = 0xFFAAAAAA;
    private static final int COLOR_OK      = 0xFF55FF55;
    private static final int COLOR_WARN    = 0xFFFF5555;
    private static final int COLOR_BG      = 0x88222222;
    private static final int COLOR_BORDER  = 0x88888888;

    private static final int COLOR_CHART_BG    = 0x66111111;
    private static final int COLOR_CHART_AXIS  = 0x88888888;
    private static final int COLOR_CHART_LINE  = 0xFF55CCFF;
    private static final int COLOR_CHART_POINT = 0xFFFFFFFF;
    private static final int COLOR_BAR_COMMON  = 0xFF55FF55;
    private static final int COLOR_BAR_DRUM    = 0xFFFFAA55;

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final Screen parent;

    private String flashMessage = null;
    private long flashUntil = 0;

    public MusixStatsScreen(Screen parent) {
        super(Text.literal("Musix 통계"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int by = this.height - 28;
        int btnW = 120, gap = 6;
        int totalW = btnW * 3 + gap * 2;
        int startX = (this.width - totalW) / 2;

        this.addDrawableChild(ButtonWidget.builder(Text.literal("통계 초기화"),
                btn -> { MusixStatus.reset(); flash("✓ 통계 초기화됨"); }
        ).dimensions(startX, by, btnW, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("← 메뉴로"),
                btn -> { if (this.client != null) this.client.setScreen(this.parent); }
        ).dimensions(startX + btnW + gap, by, btnW, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("닫기"),
                btn -> this.close()
        ).dimensions(startX + (btnW + gap) * 2, by, btnW, 20).build());
    }

    private void flash(String msg) {
        flashMessage = msg;
        flashUntil = System.currentTimeMillis() + 3000L;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        TextRenderer tr = this.textRenderer;
        int cx = this.width / 2;

        context.drawCenteredTextWithShadow(tr, "♪ Musix 통계 ♪", cx, 8, COLOR_TITLE);

        // ===== 세션 정보 박스 =====
        int sx = 20, sy = 24, sw = this.width - 40, sh = 48;
        context.fill(sx, sy, sx + sw, sy + sh, COLOR_BG);
        drawBorder(context, sx, sy, sw, sh);

        long started = MusixStatus.sessionStartMillis();
        long elapsedSec = MusixStatus.sessionElapsedMillis() / 1000L;
        long total = MusixStatus.noteCount();
        double avgPerMin = MusixStatus.averagePerMinute();
        int currentMin = MusixStatus.currentMinuteCount();
        String lastNote = MusixStatus.lastNote();

        int ty = sy + 4;
        context.drawTextWithShadow(tr, "▣ 세션 정보", sx + 6, ty, COLOR_HEADER);
        ty += 12;
        // 2열로 배치
        int col1 = sx + 10, col2 = sx + sw / 2 + 6;
        context.drawTextWithShadow(tr, "시작 시각:", col1, ty, COLOR_LABEL);
        context.drawTextWithShadow(tr, TIME_FMT.format(Instant.ofEpochMilli(started)),
                col1 + 80, ty, COLOR_VALUE);
        context.drawTextWithShadow(tr, "경과:", col2, ty, COLOR_LABEL);
        context.drawTextWithShadow(tr, formatDuration(elapsedSec), col2 + 60, ty, COLOR_VALUE);
        ty += 11;
        context.drawTextWithShadow(tr, "총 음 입력:", col1, ty, COLOR_LABEL);
        context.drawTextWithShadow(tr, String.format("%,d", total), col1 + 80, ty, COLOR_OK);
        context.drawTextWithShadow(tr, "분당 평균:", col2, ty, COLOR_LABEL);
        context.drawTextWithShadow(tr, String.format("%.1f", avgPerMin), col2 + 60, ty, COLOR_VALUE);
        ty += 11;
        context.drawTextWithShadow(tr, "현재 분 입력:", col1, ty, COLOR_LABEL);
        context.drawTextWithShadow(tr, String.valueOf(currentMin) + " /min",
                col1 + 80, ty, currentMin > 0 ? COLOR_OK : COLOR_DIM);
        context.drawTextWithShadow(tr, "마지막 음:", col2, ty, COLOR_LABEL);
        context.drawTextWithShadow(tr, lastNote == null ? "(없음)" : lastNote,
                col2 + 60, ty, lastNote == null ? COLOR_DIM : COLOR_VALUE);

        // ===== 꺾은선 그래프 (분당 입력 추이) =====
        int gx = 20, gy = sy + sh + 6, gw = this.width - 40, gh = 70;
        drawLineChart(context, tr, gx, gy, gw, gh);

        // ===== 하단 두 박스: TOP5 + Preset 비율 =====
        int bx = 20, by2 = gy + gh + 6;
        int leftW = (this.width - 40 - 6) / 2;
        int rightX = bx + leftW + 6;
        int bottomH = this.height - by2 - 38;
        drawTopNotesBox(context, tr, bx, by2, leftW, bottomH);
        drawPresetBox(context, tr, rightX, by2, leftW, bottomH);

        if (flashMessage != null && System.currentTimeMillis() < flashUntil) {
            context.drawCenteredTextWithShadow(tr, flashMessage, cx, this.height - 42, COLOR_OK);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    /** 분당 입력 꺾은선 그래프. */
    private void drawLineChart(DrawContext ctx, TextRenderer tr, int x, int y, int w, int h) {
        ctx.fill(x, y, x + w, y + h, COLOR_CHART_BG);
        drawBorder(ctx, x, y, w, h);
        ctx.drawTextWithShadow(tr, "▣ 분당 입력 추이 (최근 60분)", x + 6, y + 4, COLOR_HEADER);

        int chartTop = y + 18;
        int chartBottom = y + h - 14; // X축 라벨 영역
        int chartLeft = x + 30;        // Y축 라벨 영역
        int chartRight = x + w - 8;
        // Y축 / X축
        ctx.fill(chartLeft, chartTop, chartLeft + 1, chartBottom, COLOR_CHART_AXIS);
        ctx.fill(chartLeft, chartBottom - 1, chartRight, chartBottom, COLOR_CHART_AXIS);

        List<long[]> hist = MusixStatus.minuteHistory();
        if (hist.isEmpty()) {
            ctx.drawTextWithShadow(tr, "(아직 입력 없음)",
                    chartLeft + 4, (chartTop + chartBottom) / 2 - 4, COLOR_DIM);
            return;
        }

        // 시간 윈도우: 현재 분 - 59 ~ 현재 분
        long curMinute = System.currentTimeMillis() / 60_000L;
        long minMinute = curMinute - 59;
        // 최대값
        long maxV = 1;
        for (long[] p : hist) if (p[1] > maxV) maxV = p[1];
        // Y축 눈금 (상/중)
        ctx.drawTextWithShadow(tr, String.valueOf(maxV), x + 4, chartTop - 3, COLOR_DIM);
        ctx.drawTextWithShadow(tr, String.valueOf(maxV / 2),
                x + 4, (chartTop + chartBottom) / 2 - 3, COLOR_DIM);
        ctx.drawTextWithShadow(tr, "0", x + 4, chartBottom - 7, COLOR_DIM);
        // X축 라벨
        ctx.drawTextWithShadow(tr, "-60분", chartLeft - 4, chartBottom + 2, COLOR_DIM);
        ctx.drawTextWithShadow(tr, "지금",
                chartRight - tr.getWidth("지금"), chartBottom + 2, COLOR_DIM);

        // 점들을 X 좌표로 변환
        int chartW = chartRight - chartLeft;
        int chartH = chartBottom - chartTop;
        int[] xs = new int[60];
        int[] ys = new int[60];
        boolean[] has = new boolean[60];
        for (long[] p : hist) {
            int idx = (int) (p[0] - minMinute);
            if (idx < 0 || idx >= 60) continue;
            int px = chartLeft + (chartW * idx) / 59;
            int py = chartBottom - (int) ((chartH * p[1]) / maxV);
            xs[idx] = px;
            ys[idx] = py;
            has[idx] = true;
        }
        // 빈 분 = 0 으로 설정
        for (int i = 0; i < 60; i++) {
            if (!has[i]) {
                xs[i] = chartLeft + (chartW * i) / 59;
                ys[i] = chartBottom;
                has[i] = true;
            }
        }
        // 선 그리기 (Bresenham)
        for (int i = 0; i < 59; i++) {
            drawLine(ctx, xs[i], ys[i], xs[i + 1], ys[i + 1], COLOR_CHART_LINE);
        }
        // 점 그리기
        for (int i = 0; i < 60; i++) {
            if (ys[i] < chartBottom) { // 0 이상만
                ctx.fill(xs[i] - 1, ys[i] - 1, xs[i] + 2, ys[i] + 2, COLOR_CHART_POINT);
            }
        }
    }

    /** TOP 5 음 막대 차트. */
    private void drawTopNotesBox(DrawContext ctx, TextRenderer tr, int x, int y, int w, int h) {
        ctx.fill(x, y, x + w, y + h, COLOR_BG);
        drawBorder(ctx, x, y, w, h);
        ctx.drawTextWithShadow(tr, "▣ TOP 5 음", x + 6, y + 4, COLOR_HEADER);

        List<Map.Entry<String, Integer>> top = MusixStatus.topNotes(5);
        if (top.isEmpty()) {
            ctx.drawTextWithShadow(tr, "(데이터 없음)", x + 10, y + 20, COLOR_DIM);
            return;
        }
        int maxV = top.get(0).getValue();
        int rowH = 12, ty = y + 18;
        int barX = x + 70;
        int barMaxW = w - 78;
        for (Map.Entry<String, Integer> e : top) {
            String name = e.getKey();
            int v = e.getValue();
            int barW = Math.max(1, (barMaxW * v) / maxV);
            ctx.drawTextWithShadow(tr, ellipsize(name, 60, tr), x + 6, ty, COLOR_VALUE);
            ctx.fill(barX, ty - 1, barX + barW, ty + 8, COLOR_CHART_LINE);
            ctx.drawTextWithShadow(tr, String.valueOf(v), barX + barW + 4, ty, COLOR_OK);
            ty += rowH;
        }
    }

    /** Preset 비율 (common vs drum). */
    private void drawPresetBox(DrawContext ctx, TextRenderer tr, int x, int y, int w, int h) {
        ctx.fill(x, y, x + w, y + h, COLOR_BG);
        drawBorder(ctx, x, y, w, h);
        ctx.drawTextWithShadow(tr, "▣ Preset 비율", x + 6, y + 4, COLOR_HEADER);

        Map<String, Integer> pc = MusixStatus.presetCounts();
        int total = pc.values().stream().mapToInt(Integer::intValue).sum();
        if (total == 0) {
            ctx.drawTextWithShadow(tr, "(데이터 없음)", x + 10, y + 20, COLOR_DIM);
            return;
        }
        int common = pc.getOrDefault("common", 0);
        int drum = pc.getOrDefault("drum", 0);
        double pCommon = 100.0 * common / total;
        double pDrum = 100.0 * drum / total;

        int ty = y + 18, rowH = 14;
        int barX = x + 60;
        int barMaxW = w - 100;

        // Common
        ctx.drawTextWithShadow(tr, "common", x + 6, ty, COLOR_LABEL);
        int cW = (int) (barMaxW * common / (double) total);
        ctx.fill(barX, ty - 1, barX + cW, ty + 8, COLOR_BAR_COMMON);
        ctx.drawTextWithShadow(tr, String.format("%.0f%% (%d)", pCommon, common),
                barX + barMaxW + 4, ty, COLOR_VALUE);
        ty += rowH;
        // Drum
        ctx.drawTextWithShadow(tr, "drum", x + 6, ty, COLOR_LABEL);
        int dW = (int) (barMaxW * drum / (double) total);
        ctx.fill(barX, ty - 1, barX + dW, ty + 8, COLOR_BAR_DRUM);
        ctx.drawTextWithShadow(tr, String.format("%.0f%% (%d)", pDrum, drum),
                barX + barMaxW + 4, ty, COLOR_VALUE);
        ty += rowH + 6;

        // 합계
        ctx.drawTextWithShadow(tr, "합계: " + total + "음", x + 6, ty, COLOR_DIM);
    }

    private String ellipsize(String s, int maxW, TextRenderer tr) {
        if (tr.getWidth(s) <= maxW) return s;
        while (s.length() > 1 && tr.getWidth(s + "...") > maxW) s = s.substring(0, s.length() - 1);
        return s + "...";
    }

    private String formatDuration(long sec) {
        long h = sec / 3600;
        long m = (sec % 3600) / 60;
        long s = sec % 60;
        if (h > 0) return String.format("%dh %dm %ds", h, m, s);
        if (m > 0) return String.format("%dm %ds", m, s);
        return s + "s";
    }

    /** Bresenham 알고리즘으로 선 그리기 (DrawContext.fill 사용). */
    private static void drawLine(DrawContext ctx, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int steps = 0, maxSteps = (dx + dy + 2) * 2; // 무한루프 안전망
        while (steps++ < maxSteps) {
            ctx.fill(x0, y0, x0 + 1, y0 + 1, color);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x0 += sx; }
            if (e2 < dx)  { err += dx; y0 += sy; }
        }
    }

    private void drawBorder(DrawContext ctx, int x, int y, int w, int h) {
        ctx.fill(x,         y,         x + w,     y + 1,     COLOR_BORDER);
        ctx.fill(x,         y + h - 1, x + w,     y + h,     COLOR_BORDER);
        ctx.fill(x,         y,         x + 1,     y + h,     COLOR_BORDER);
        ctx.fill(x + w - 1, y,         x + w,     y + h,     COLOR_BORDER);
    }

    @Override
    public void close() {
        if (this.client != null) this.client.setScreen(this.parent);
    }

    @Override
    public boolean shouldPause() { return false; }
}

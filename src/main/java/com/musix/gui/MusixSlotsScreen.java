package com.musix.gui;

import com.musix.MusixClient;
import com.musix.config.MappingSlots;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.nio.file.Path;
import java.util.List;

/**
 * v3.10.0: 매핑 슬롯 관리 화면.
 * - 현재 매핑을 이름으로 저장
 * - 저장된 슬롯 목록 → 불러오기/내보내기/삭제
 * - config 폴더의 외부 export JSON → import
 */
public class MusixSlotsScreen extends Screen {
    private static final int COLOR_TITLE  = 0xFFFFFFFF;
    private static final int COLOR_HEADER = 0xFF88CCFF;
    private static final int COLOR_LABEL  = 0xFFFFAA00;
    private static final int COLOR_VALUE  = 0xFFFFFFFF;
    private static final int COLOR_DIM    = 0xFFAAAAAA;
    private static final int COLOR_OK     = 0xFF55FF55;
    private static final int COLOR_WARN   = 0xFFFF5555;
    private static final int COLOR_BG     = 0x88222222;
    private static final int COLOR_BORDER = 0x88888888;
    private static final int ROW_HEIGHT   = 14;

    private final Screen parent;
    private TextFieldWidget nameField;
    private ButtonWidget saveBtn;
    private List<String> slotNames;
    private List<Path> importFiles;

    private int listX, listY, listW, listH;
    private int importY;
    private double scroll = 0;

    private String flashMessage = null;
    private long flashUntil = 0;
    private boolean flashSuccess = true;

    public MusixSlotsScreen(Screen parent) {
        super(Text.literal("Musix 매핑 슬롯"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        slotNames = MappingSlots.listNames();
        importFiles = MappingSlots.findImportableFiles();

        int cx = this.width / 2;
        // 상단 입력 + 저장 버튼
        int topY = 44;
        int fieldW = 200, btnW = 80, gap = 6;
        int totalW = fieldW + gap + btnW;
        int startX = cx - totalW / 2;

        nameField = new TextFieldWidget(this.textRenderer, startX, topY, fieldW, 18,
                Text.literal("슬롯 이름"));
        nameField.setMaxLength(32);
        nameField.setPlaceholder(Text.literal("슬롯 이름 (한글 가능)"));
        this.addDrawableChild(nameField);

        saveBtn = ButtonWidget.builder(Text.literal("저장"),
                btn -> doSave()
        ).dimensions(startX + fieldW + gap, topY, btnW, 18).build();
        this.addDrawableChild(saveBtn);

        // 하단 버튼들
        int by = this.height - 28;
        int btnW2 = 120, gap2 = 6;
        int totalW2 = btnW2 * 3 + gap2 * 2;
        int startX2 = (this.width - totalW2) / 2;

        this.addDrawableChild(ButtonWidget.builder(Text.literal("슬롯 폴더 열기"),
                btn -> Util.getOperatingSystem().open(MappingSlots.slotDir().toFile())
        ).dimensions(startX2, by, btnW2, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("← 고급 설정으로"),
                btn -> { if (this.client != null) this.client.setScreen(this.parent); }
        ).dimensions(startX2 + btnW2 + gap2, by, btnW2, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("닫기"),
                btn -> this.close()
        ).dimensions(startX2 + (btnW2 + gap2) * 2, by, btnW2, 20).build());
    }

    private void refreshLists() {
        slotNames = MappingSlots.listNames();
        importFiles = MappingSlots.findImportableFiles();
    }

    private void flash(String msg, boolean ok) {
        this.flashMessage = msg;
        this.flashSuccess = ok;
        this.flashUntil = System.currentTimeMillis() + 4000L;
    }

    private void doSave() {
        String name = nameField.getText();
        if (!MappingSlots.isValidName(name)) {
            flash("✗ 이름이 유효하지 않음 (1~32자, / \\ .. 금지)", false);
            return;
        }
        boolean ok = MappingSlots.save(name, MusixClient.config());
        flash(ok ? "✓ 저장: '" + name + "'" : "✗ 저장 실패", ok);
        if (ok) {
            nameField.setText("");
            refreshLists();
        }
    }

    private void doLoad(String name) {
        boolean ok = MappingSlots.load(name);
        if (ok) MusixClient.reloadConfig();
        flash(ok ? "✓ 불러옴: '" + name + "'" : "✗ 불러오기 실패", ok);
    }

    private void doExport(String name) {
        Path out = MappingSlots.exportToConfigDir(name);
        flash(out != null ? "✓ 내보냄: " + out.getFileName() : "✗ 내보내기 실패", out != null);
    }

    private void doDelete(String name) {
        boolean ok = MappingSlots.delete(name);
        flash(ok ? "✓ 삭제: '" + name + "'" : "✗ 삭제 실패", ok);
        if (ok) refreshLists();
    }

    private void doImport(Path file) {
        String slotName = nameField.getText().trim();
        if (slotName.isEmpty()) {
            // 자동: 파일명에서 musix-export- 접두사 제거 + .json 제거
            String n = file.getFileName().toString().replaceFirst("\\.json$", "");
            if (n.startsWith("musix-export-")) n = n.substring("musix-export-".length());
            slotName = n;
        }
        if (!MappingSlots.isValidName(slotName)) {
            flash("✗ 슬롯 이름이 유효하지 않음", false);
            return;
        }
        boolean ok = MappingSlots.importFile(file, slotName);
        flash(ok ? "✓ 가져옴: '" + slotName + "'" : "✗ 가져오기 실패", ok);
        if (ok) refreshLists();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        TextRenderer tr = this.textRenderer;
        int cx = this.width / 2;

        context.drawCenteredTextWithShadow(tr, "♪ Musix 매핑 슬롯 관리 ♪", cx, 12, COLOR_TITLE);
        context.drawCenteredTextWithShadow(tr,
                "현재 매핑을 이름으로 저장하거나, 저장된 매핑을 불러올 수 있습니다",
                cx, 24, COLOR_DIM);

        // 슬롯 목록 박스
        listX = 40;
        listY = 70;
        listW = this.width - 80;
        listH = this.height - 70 - 60; // 상단 + 하단 버튼 영역 빼고

        context.fill(listX, listY, listX + listW, listY + listH, COLOR_BG);
        drawBorder(context, listX, listY, listW, listH);

        // 헤더
        context.drawTextWithShadow(tr, "▣ 저장된 슬롯 (" + slotNames.size() + ")",
                listX + 8, listY + 6, COLOR_HEADER);
        context.drawTextWithShadow(tr, "이름 / 시각",
                listX + 8, listY + 18, COLOR_DIM);
        context.drawTextWithShadow(tr, "작업",
                listX + listW - 200, listY + 18, COLOR_DIM);

        // 슬롯 목록 + import 목록
        int contentY = listY + 32;
        int contentH = listH - 38;
        int rowY = contentY - (int) scroll;
        int maxRows = contentH / ROW_HEIGHT;

        // === 저장된 슬롯 ===
        for (int i = 0; i < slotNames.size(); i++) {
            int y = rowY + i * ROW_HEIGHT;
            if (y < contentY - ROW_HEIGHT || y > contentY + contentH) continue;
            renderSlotRow(context, tr, slotNames.get(i), y, mouseX, mouseY);
        }

        // === Import 가능 파일 (저장된 슬롯 다음에 한 줄 간격) ===
        importY = rowY + slotNames.size() * ROW_HEIGHT + 6;
        if (!importFiles.isEmpty()) {
            context.drawTextWithShadow(tr, "▣ Import (config 폴더의 export 파일)",
                    listX + 8, importY, COLOR_HEADER);
            importY += 12;
            for (int i = 0; i < importFiles.size(); i++) {
                int y = importY + i * ROW_HEIGHT;
                if (y < contentY - ROW_HEIGHT || y > contentY + contentH) continue;
                renderImportRow(context, tr, importFiles.get(i), y, mouseX, mouseY);
            }
        } else {
            context.drawTextWithShadow(tr,
                    "Import 가능한 musix-export-*.json 파일이 config 폴더에 없습니다.",
                    listX + 8, importY, COLOR_DIM);
        }

        // 플래시 메시지
        if (flashMessage != null && System.currentTimeMillis() < flashUntil) {
            context.drawCenteredTextWithShadow(tr, flashMessage, cx, this.height - 42,
                    flashSuccess ? COLOR_OK : COLOR_WARN);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderSlotRow(DrawContext ctx, TextRenderer tr, String name, int y,
                               int mouseX, int mouseY) {
        // 호버 배경
        boolean hover = mouseY >= y - 1 && mouseY < y + ROW_HEIGHT - 1
                && mouseX >= listX + 4 && mouseX < listX + listW - 4;
        if (hover) ctx.fill(listX + 4, y - 1, listX + listW - 4, y + ROW_HEIGHT - 1, 0x33FFFFFF);

        ctx.drawTextWithShadow(tr, name, listX + 8, y + 2, COLOR_VALUE);
        String savedAt = MappingSlots.savedAt(name);
        if (!savedAt.isEmpty() && savedAt.length() >= 19) {
            ctx.drawTextWithShadow(tr, savedAt.substring(0, 19).replace('T', ' '),
                    listX + 130, y + 2, COLOR_DIM);
        }
        // 액션 텍스트 버튼 (오른쪽)
        int rx = listX + listW - 200;
        ctx.drawTextWithShadow(tr, "[불러오기]",  rx,        y + 2, COLOR_OK);
        ctx.drawTextWithShadow(tr, "[내보내기]",  rx + 65,   y + 2, COLOR_HEADER);
        ctx.drawTextWithShadow(tr, "[삭제]",      rx + 132,  y + 2, COLOR_WARN);
    }

    private void renderImportRow(DrawContext ctx, TextRenderer tr, Path file, int y,
                                 int mouseX, int mouseY) {
        boolean hover = mouseY >= y - 1 && mouseY < y + ROW_HEIGHT - 1
                && mouseX >= listX + 4 && mouseX < listX + listW - 4;
        if (hover) ctx.fill(listX + 4, y - 1, listX + listW - 4, y + ROW_HEIGHT - 1, 0x33FFFFFF);

        String fn = file.getFileName().toString();
        if (fn.length() > 50) fn = fn.substring(0, 47) + "...";
        ctx.drawTextWithShadow(tr, fn, listX + 8, y + 2, COLOR_VALUE);
        int rx = listX + listW - 100;
        ctx.drawTextWithShadow(tr, "[가져오기]", rx, y + 2, COLOR_OK);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;

        int contentY = listY + 32;
        int rowY = contentY - (int) scroll;

        // 저장된 슬롯 행 클릭
        for (int i = 0; i < slotNames.size(); i++) {
            int y = rowY + i * ROW_HEIGHT;
            if (mouseY < y - 1 || mouseY >= y + ROW_HEIGHT - 1) continue;
            int rx = listX + listW - 200;
            if (mouseX >= rx && mouseX < rx + 60) {
                doLoad(slotNames.get(i));
                return true;
            }
            if (mouseX >= rx + 65 && mouseX < rx + 65 + 65) {
                doExport(slotNames.get(i));
                return true;
            }
            if (mouseX >= rx + 132 && mouseX < rx + 132 + 40) {
                doDelete(slotNames.get(i));
                return true;
            }
        }

        // Import 행 클릭
        int importBaseY = rowY + slotNames.size() * ROW_HEIGHT + 6 + 12;
        for (int i = 0; i < importFiles.size(); i++) {
            int y = importBaseY + i * ROW_HEIGHT;
            if (mouseY < y - 1 || mouseY >= y + ROW_HEIGHT - 1) continue;
            int rx = listX + listW - 100;
            if (mouseX >= rx && mouseX < rx + 80) {
                doImport(importFiles.get(i));
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        scroll -= amount * 12;
        int totalRows = slotNames.size() + (importFiles.isEmpty() ? 0 : importFiles.size() + 2);
        double maxScroll = Math.max(0, totalRows * ROW_HEIGHT - (listH - 38));
        if (scroll < 0) scroll = 0;
        if (scroll > maxScroll) scroll = maxScroll;
        return true;
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

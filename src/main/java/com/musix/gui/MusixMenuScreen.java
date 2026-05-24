package com.musix.gui;

import com.musix.MusixClient;
import com.musix.config.LastSeenContainer;
import com.musix.config.MusixConfig;
import com.musix.config.MusixStatus;
import com.musix.key.KeyBindings;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.KeybindsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class MusixMenuScreen extends Screen {
    private static final int COLOR_TITLE        = 0xFFFFFFFF;
    private static final int COLOR_VERSION      = 0xFFAAAAAA;
    private static final int COLOR_LABEL        = 0xFFFFAA00;
    private static final int COLOR_VALUE        = 0xFFFFFFFF;
    private static final int COLOR_OK           = 0xFF55FF55;
    private static final int COLOR_WARN         = 0xFFFF5555;
    private static final int COLOR_HEADER       = 0xFF88CCFF;
    private static final int COLOR_AWAITING     = 0xFFFFFF55;
    private static final int COLOR_BORDER       = 0x88888888;
    private static final int COLOR_BG           = 0x88222222;
    private static final int COLOR_ROW_ALT      = 0x22FFFFFF;
    private static final int COLOR_ROW_AWAIT    = 0x66FFFF00;
    private static final int COLOR_ROW_CONFLICT = 0x99FF3333;
    private static final int ROW_HEIGHT         = 11;
    private static final long CONFLICT_FLASH_MS = 2500L;
    private static final int CONFLICT_MENU_KEY  = -2;

    private static final String[] CLICK_ACTIONS = {"PICKUP", "QUICK_MOVE", "SWAP", "CLONE", "THROW", "PICKUP_ALL"};
    private static final String[] CLICK_BUTTONS = {"0 (좌클릭)", "1 (우클릭)", "2 (중클릭)"};

    private final Screen parent;
    private int scroll = 0;
    private int listX, listW, listTop, listBottom;
    private int awaitingIndex = -1;
    private int conflictIndex = -1;
    private long conflictUntil = 0L;
    private int rowYClickButton, rowYClickAction, rowYDebug, rowYAutoMap, rowYPreset;
    private String autoMapMessage = null;
    private long autoMapUntil = 0L;
    private boolean autoMapSuccess = false;
    /** 사용자가 메뉴에서 선택해서 보고 있는 preset (null=자동 활성) */
    private String selectedPreset = null;
    /** 탭 클릭 영역 (render 마다 갱신) */
    private final List<TabArea> tabAreas = new ArrayList<>();

    private record TabArea(String preset, int x1, int x2) {}

    public MusixMenuScreen(Screen parent) {
        super(Text.literal("Musix"));
        this.parent = parent;
    }

    private String currentPreset() {
        return selectedPreset != null ? selectedPreset : KeyBindings.activePresetName();
    }

    @Override
    protected void init() {
        int by = this.height - 28;
        int btnW = 110, gap = 6;
        int totalW = btnW * 4 + gap * 3;
        int startX = (this.width - totalW) / 2;

        this.addDrawableChild(ButtonWidget.builder(Text.literal("게임 키 설정"), btn -> {
            if (this.client != null) this.client.setScreen(new KeybindsScreen(this, this.client.options));
        }).dimensions(startX, by, btnW, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("고급 설정"),
                btn -> { if (this.client != null) this.client.setScreen(new MusixAdvancedScreen(this)); }
        ).dimensions(startX + btnW + gap, by, btnW, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("⚠ 선택 preset 초기화"), btn -> {
            MusixClient.config().resetSettingsToDefaults();
            KeyBindings.resetPreset(currentPreset());
            awaitingIndex = -1;
        }).dimensions(startX + (btnW + gap) * 2, by, btnW, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("닫기"), btn -> this.close())
                .dimensions(startX + (btnW + gap) * 3, by, btnW, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        TextRenderer tr = this.textRenderer;
        int cx = this.width / 2;

        context.drawCenteredTextWithShadow(tr, "♪ Musix ♪", cx, 8, COLOR_TITLE);
        context.drawCenteredTextWithShadow(tr, "v" + MusixClient.version(), cx, 20, COLOR_VERSION);

        // ===== 상태 박스 (디버그/접두사 라인 모두 제거 → 고급 설정 화면으로 이동) =====
        int statusY = 36, statusX = 20;
        int statusW = this.width - 40, statusH = 93;
        context.fill(statusX, statusY, statusX + statusW, statusY + statusH, COLOR_BG);
        drawBorder(context, statusX, statusY, statusW, statusH);

        int y = statusY + 5;
        context.drawTextWithShadow(tr, "▣ 상태", statusX + 6, y, COLOR_HEADER);
        y += 12;

        MusixConfig cfg = MusixClient.config();
        String active = KeyBindings.activePresetName();
        String shown = currentPreset();

        rowYClickButton = y;
        context.drawTextWithShadow(tr, "클릭 버튼:", statusX + 10, y, COLOR_LABEL);
        int bIdx = Math.max(0, Math.min(2, cfg.clickButton));
        context.drawTextWithShadow(tr, CLICK_BUTTONS[bIdx] + "  [클릭으로 변경]", statusX + 110, y, COLOR_AWAITING);
        y += 11;

        rowYClickAction = y;
        context.drawTextWithShadow(tr, "클릭 동작:", statusX + 10, y, COLOR_LABEL);
        context.drawTextWithShadow(tr, (cfg.clickAction == null ? "PICKUP" : cfg.clickAction) + "  [클릭으로 변경]",
                statusX + 110, y, COLOR_AWAITING);
        y += 11;

        rowYDebug = -1; // 고급 설정 화면으로 이동

        rowYAutoMap = y;
        context.drawTextWithShadow(tr, "자동 매핑:", statusX + 10, y, COLOR_LABEL);
        int cacheSlots = LastSeenContainer.nonEmptySlots().size();
        String cacheTitle = LastSeenContainer.title();
        String autoMapText;
        int autoMapColor;
        long now = System.currentTimeMillis();
        if (autoMapMessage != null && autoMapUntil > now) {
            autoMapText = autoMapMessage;
            autoMapColor = autoMapSuccess ? COLOR_OK : COLOR_WARN;
        } else if (cacheSlots == 0) {
            autoMapText = "(상자 안 열어봄)";
            autoMapColor = COLOR_VERSION;
        } else {
            autoMapText = "캐시 " + cacheSlots + "슬롯" + (cacheTitle != null ? " ('" + cacheTitle + "')" : "")
                    + "  [클릭으로 활성 preset에 적용]";
            autoMapColor = COLOR_AWAITING;
        }
        context.drawTextWithShadow(tr, autoMapText, statusX + 110, y, autoMapColor);
        y += 11;

        long sinceLast = MusixStatus.millisSinceLastNote();
        context.drawTextWithShadow(tr, "마지막 음 입력:", statusX + 10, y, COLOR_LABEL);
        if (sinceLast < 0) {
            context.drawTextWithShadow(tr, "(아직 없음)", statusX + 110, y, COLOR_WARN);
        } else {
            String note = MusixStatus.lastNote();
            context.drawTextWithShadow(tr, (note == null ? "?" : note)
                    + "  (슬롯 " + MusixStatus.lastSlot() + ", " + (sinceLast / 1000L) + "초 전, 총 "
                    + MusixStatus.noteCount() + "회)", statusX + 110, y, COLOR_OK);
        }

        // ===== preset 탭 UI =====
        listX = 20;
        listW = this.width - 40;
        int tabsY = statusY + statusH + 8;
        int tabH = 14;
        rowYPreset = tabsY;
        tabAreas.clear();

        int tx = listX;
        for (String preset : MusixConfig.ALL_PRESETS) {
            int noteCount = KeyBindings.getNotes(preset).size();
            String name = cfg.displayNameOf(preset) + " (" + noteCount + ")";
            String label = (preset.equals(active) ? "● " : "") + name;
            // 폭은 "● 마커 포함" 기준으로 예약 → 활성/비활성 모두 동일 폭
            int w = tr.getWidth("● " + name) + 16;
            boolean isShown = preset.equals(shown);
            int bgColor    = isShown ? 0xCC4488CC : 0x88222222;
            int borderCol  = isShown ? 0xFF88CCFF : COLOR_BORDER;
            int textColor  = isShown ? 0xFFFFFFFF : COLOR_VALUE;
            context.fill(tx, tabsY, tx + w, tabsY + tabH, bgColor);
            context.fill(tx,         tabsY,             tx + w,     tabsY + 1,         borderCol);
            context.fill(tx,         tabsY + tabH - 1,  tx + w,     tabsY + tabH,      borderCol);
            context.fill(tx,         tabsY,             tx + 1,     tabsY + tabH,      borderCol);
            context.fill(tx + w - 1, tabsY,             tx + w,     tabsY + tabH,      borderCol);
            // 텍스트 가운데 정렬
            int textW = tr.getWidth(label);
            int textX = tx + (w - textW) / 2;
            context.drawTextWithShadow(tr, label, textX, tabsY + 3, textColor);
            tabAreas.add(new TabArea(preset, tx, tx + w));
            tx += w + 4;
        }
        // 탭 옆 마지막 상자 제목 표시 제거 (v3.4.1)

        // 컬럼 라벨 + 리스트
        int colLabelY = tabsY + tabH + 6;
        listTop = colLabelY + 12;
        listBottom = this.height - 38;

        int colNote = listX + 12, colSlot = listX + 90, colKey = listX + 160;
        context.drawTextWithShadow(tr, "노트",    colNote, colLabelY, COLOR_LABEL);
        context.drawTextWithShadow(tr, "슬롯",    colSlot, colLabelY, COLOR_LABEL);
        context.drawTextWithShadow(tr, "현재 키", colKey,  colLabelY, COLOR_LABEL);
        String countHeader = "사용";
        context.drawTextWithShadow(tr, countHeader, listX + listW - tr.getWidth(countHeader) - 10,
                colLabelY, COLOR_LABEL);

        context.fill(listX, listTop, listX + listW, listBottom, COLOR_BG);
        drawBorder(context, listX, listTop, listW, listBottom - listTop);

        now = System.currentTimeMillis();
        boolean conflictActive = conflictUntil > now;
        if (!conflictActive) conflictIndex = -1;

        List<KeyBindings.NoteEntry> notes = KeyBindings.getNotes(shown);

        context.enableScissor(listX + 1, listTop + 1, listX + listW - 1, listBottom - 1);
        int rowIndex = 0;
        for (KeyBindings.NoteEntry note : notes) {
            int rowY = listTop + 3 + rowIndex * ROW_HEIGHT - scroll;
            if (rowY + ROW_HEIGHT >= listTop && rowY < listBottom) {
                if (rowIndex == awaitingIndex) {
                    context.fill(listX + 1, rowY - 1, listX + listW - 1, rowY + ROW_HEIGHT - 1, COLOR_ROW_AWAIT);
                } else if (conflictActive && rowIndex == conflictIndex) {
                    context.fill(listX + 1, rowY - 1, listX + listW - 1, rowY + ROW_HEIGHT - 1, COLOR_ROW_CONFLICT);
                } else if ((rowIndex & 1) == 1) {
                    context.fill(listX + 1, rowY - 1, listX + listW - 1, rowY + ROW_HEIGHT - 1, COLOR_ROW_ALT);
                }
                String noteName = note.mapping().note;
                boolean isSharp = noteName != null && noteName.contains("#");
                int noteColor;
                if (conflictActive && rowIndex == conflictIndex) noteColor = COLOR_WARN;
                else if (isSharp) noteColor = COLOR_VERSION; // 반음: 회색
                else              noteColor = COLOR_VALUE;   // 본음: 흰색
                context.drawTextWithShadow(tr, noteName, colNote, rowY, noteColor);
                context.drawTextWithShadow(tr, "#" + note.mapping().slot, colSlot, rowY, COLOR_VERSION);
                if (rowIndex == awaitingIndex) {
                    context.drawTextWithShadow(tr, "▶ 키 입력 (Shift/Alt/Space 조합 가능, ESC=기본값)",
                            colKey, rowY, COLOR_AWAITING);
                } else {
                    int keyColor = note.isUnbound() ? COLOR_WARN
                            : (conflictActive && rowIndex == conflictIndex ? COLOR_WARN : COLOR_VALUE);
                    context.drawTextWithShadow(tr, note.displayKey(), colKey, rowY, keyColor);
                    // 사용 횟수 표시
                    int cnt = MusixStatus.countOf(note.mapping().note);
                    if (cnt > 0) {
                        String cntStr = "× " + cnt;
                        context.drawTextWithShadow(tr, cntStr, listX + listW - tr.getWidth(cntStr) - 10,
                                rowY, COLOR_OK);
                    }
                }
            }
            rowIndex++;
        }
        context.disableScissor();

        String help;
        int helpColor;
        if (conflictActive) {
            if (conflictIndex == CONFLICT_MENU_KEY)      help = "⚠ 메뉴 키와 충돌 — 변경 거부됨";
            else if (conflictIndex >= 0 && conflictIndex < notes.size())
                help = "⚠ '" + notes.get(conflictIndex).mapping().note + "' 이미 사용 중";
            else help = "⚠ 키 충돌";
            helpColor = COLOR_WARN;
        } else if (awaitingIndex >= 0) {
            help = "▶ 매핑할 키를 누르세요 (Shift/Alt/Space 조합 가능). ESC=기본값";
            helpColor = COLOR_AWAITING;
        } else {
            help = "Musix 상자에서 키 누르면 음 재생 (상자 제목별로 preset 자동 선택)";
            helpColor = COLOR_VERSION;
        }
        context.drawCenteredTextWithShadow(tr, help, cx, this.height - 50, helpColor);

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
        if (button != 0 && button != 1) return false;

        // preset 탭 클릭
        if (mouseY >= rowYPreset && mouseY < rowYPreset + 14) {
            for (TabArea ta : tabAreas) {
                if (mouseX >= ta.x1 && mouseX < ta.x2) {
                    selectedPreset = ta.preset;
                    scroll = 0;
                    awaitingIndex = -1;
                    return true;
                }
            }
        }
        if (mouseY >= rowYClickButton && mouseY < rowYClickButton + ROW_HEIGHT) {
            MusixConfig cfg = MusixClient.config();
            int next = (cfg.clickButton + (button == 1 ? CLICK_BUTTONS.length - 1 : 1)) % CLICK_BUTTONS.length;
            cfg.setClickButton(next);
            return true;
        }
        if (mouseY >= rowYClickAction && mouseY < rowYClickAction + ROW_HEIGHT) {
            MusixConfig cfg = MusixClient.config();
            int idx = indexOf(CLICK_ACTIONS, cfg.clickAction);
            int next = (idx + (button == 1 ? CLICK_ACTIONS.length - 1 : 1)) % CLICK_ACTIONS.length;
            cfg.setClickAction(CLICK_ACTIONS[next]);
            return true;
        }
        // 디버그 라인은 고급 설정 화면에서만
        if (mouseY >= rowYAutoMap && mouseY < rowYAutoMap + ROW_HEIGHT) {
            // v3.8.0: 이름 기반 매핑 먼저 시도, 0매칭이면 인덱스 기반으로 폴백.
            KeyBindings.AutoMapResult result = KeyBindings.autoMapFromItemNames();
            if (!result.success()) {
                KeyBindings.AutoMapResult fallback = KeyBindings.autoMapFromLastContainer();
                if (fallback.success()) result = fallback;
            }
            autoMapMessage = result.message();
            autoMapSuccess = result.success();
            autoMapUntil = System.currentTimeMillis() + 4000L;
            return true;
        }

        if (mouseX >= listX && mouseX <= listX + listW
                && mouseY >= listTop && mouseY <= listBottom) {
            int relY = (int) mouseY - (listTop + 3) + scroll;
            if (relY >= 0) {
                int row = relY / ROW_HEIGHT;
                if (row >= 0 && row < KeyBindings.getNotes(currentPreset()).size()) {
                    if (button == 1) {
                        applyKeyChange(row, InputUtil.UNKNOWN_KEY, 0);
                        awaitingIndex = -1;
                    } else {
                        awaitingIndex = row;
                    }
                    return true;
                }
            }
            awaitingIndex = -1;
            return true;
        }
        return false;
    }

    private void cyclePreset(int direction) {
        List<String> all = MusixConfig.ALL_PRESETS;
        int idx = all.indexOf(currentPreset());
        if (idx < 0) idx = 0;
        idx = (idx + direction + all.size()) % all.size();
        selectedPreset = all.get(idx);
        scroll = 0;
    }

    private static int indexOf(String[] arr, String value) {
        if (value == null) return 0;
        for (int i = 0; i < arr.length; i++) if (arr[i].equals(value)) return i;
        return 0;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (mouseY >= listTop && mouseY <= listBottom) {
            scroll -= (int) (amount * ROW_HEIGHT * 2);
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    private void clampScroll() {
        int contentH = KeyBindings.getNotes(currentPreset()).size() * ROW_HEIGHT + 6;
        int viewH = listBottom - listTop;
        int maxScroll = Math.max(0, contentH - viewH);
        if (scroll < 0) scroll = 0;
        if (scroll > maxScroll) scroll = maxScroll;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (awaitingIndex >= 0) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                tryResetToDefault(awaitingIndex);
                awaitingIndex = -1;
                return true;
            }
            // modifier 키 자체는 매핑 안 함 — 사용자가 Shift+1, Space+1 같이 누르도록 대기
            if (isModifierOnlyKey(keyCode)) return true;
            // v3.12.0: Space 도 modifier 로 취급 (단독 매핑 불가, 조합 전용)
            if (keyCode == GLFW.GLFW_KEY_SPACE) return true;

            // v3.12.0: Space 가 눌려있으면 MOD_SPACE 비트 추가
            int effectiveMods = KeyBindings.augmentModsWithSpace(modifiers);
            int mods = effectiveMods & KeyBindings.MOD_MASK;
            if (hasConflict(awaitingIndex, keyCode, scanCode, mods)) return true;
            InputUtil.Key key = InputUtil.fromKeyCode(keyCode, scanCode);
            applyKeyChange(awaitingIndex, key, mods);
            awaitingIndex = -1;
            return true;
        }
        KeyBinding menu = KeyBindings.getMenuBinding();
        if (menu != null && menu.matchesKey(keyCode, scanCode)) {
            this.close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private static boolean isModifierOnlyKey(int keyCode) {
        // Ctrl 도 modifier 키로 인식해서 단독 누름은 무시 (조합 매핑 기능은 비활성화, Ctrl 키 자체로 매핑 안 됨)
        return keyCode == GLFW.GLFW_KEY_LEFT_SHIFT || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT
                || keyCode == GLFW.GLFW_KEY_LEFT_CONTROL || keyCode == GLFW.GLFW_KEY_RIGHT_CONTROL
                || keyCode == GLFW.GLFW_KEY_LEFT_ALT || keyCode == GLFW.GLFW_KEY_RIGHT_ALT;
    }

    private boolean hasConflict(int ownIndex, int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_UNKNOWN && scanCode == 0) return false;
        if (modifiers == 0 && KeyBindings.menuKeyMatches(keyCode, scanCode)) {
            conflictIndex = CONFLICT_MENU_KEY;
            conflictUntil = System.currentTimeMillis() + CONFLICT_FLASH_MS;
            return true;
        }
        int existing = KeyBindings.findNoteIndexByKey(currentPreset(), keyCode, scanCode, modifiers);
        if (existing >= 0 && existing != ownIndex) {
            conflictIndex = existing;
            conflictUntil = System.currentTimeMillis() + CONFLICT_FLASH_MS;
            return true;
        }
        return false;
    }

    private void tryResetToDefault(int index) {
        KeyBindings.NoteEntry note = KeyBindings.getNote(currentPreset(), index);
        if (note == null) return;
        String defaultTk = MusixConfig.lookupDefaultKeyForSlot(note.mapping().preset, note.mapping().slot);
        if (defaultTk == null) return;
        InputUtil.Key defaultKey;
        try {
            defaultKey = defaultTk.isEmpty() ? InputUtil.UNKNOWN_KEY : InputUtil.fromTranslationKey(defaultTk);
        } catch (IllegalArgumentException e) { return; }
        int kc = defaultKey.getCategory() == InputUtil.Type.KEYSYM ? defaultKey.getCode() : GLFW.GLFW_KEY_UNKNOWN;
        int sc = defaultKey.getCategory() == InputUtil.Type.SCANCODE ? defaultKey.getCode() : 0;
        if (hasConflict(index, kc, sc, 0)) return;
        KeyBindings.resetNote(note);
    }

    private void applyKeyChange(int index, InputUtil.Key key, int modifiers) {
        KeyBindings.NoteEntry note = KeyBindings.getNote(currentPreset(), index);
        if (note == null) return;
        note.setKey(key, modifiers);
    }

    @Override
    public void close() {
        awaitingIndex = -1;
        if (this.client != null) this.client.setScreen(this.parent);
    }

    @Override
    public boolean shouldPause() { return false; }
}

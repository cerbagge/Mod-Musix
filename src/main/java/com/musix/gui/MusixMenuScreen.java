package com.musix.gui;

import com.musix.MusixClient;
import com.musix.config.LastSeenContainer;
import com.musix.config.MusixConfig;
import com.musix.config.MusixStatus;
import com.musix.key.KeyBindings;
import com.musix.volume.VolumeController;
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
    private boolean awaitingSecondary = false; // v4.2.0: true면 보조 키 편집 대기
    private int colMainKeyX, colSecKeyX;        // v4.2.0: 메인/보조 키 칸 X (클릭 히트박스 공유)
    private int conflictIndex = -1;
    private long conflictUntil = 0L;
    private int rowYClickButton, rowYClickAction, rowYDebug, rowYAutoMap, rowYPreset;
    /** v5.1.0: 상단 이조 컨트롤 줄 Y / 값 텍스트 중심 X (버튼 사이). */
    private int transposeRowY, transposeTextX;
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
        int btnW = 95, gap = 5;
        int totalW = btnW * 5 + gap * 4;
        int startX = (this.width - totalW) / 2;

        this.addDrawableChild(ButtonWidget.builder(Text.literal("게임 키"), btn -> {
            if (this.client != null) this.client.setScreen(new KeybindsScreen(this, this.client.options));
        }).dimensions(startX, by, btnW, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("통계"),
                btn -> { if (this.client != null) this.client.setScreen(new MusixStatsScreen(this)); }
        ).dimensions(startX + (btnW + gap), by, btnW, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("고급 설정"),
                btn -> { if (this.client != null) this.client.setScreen(new MusixAdvancedScreen(this)); }
        ).dimensions(startX + (btnW + gap) * 2, by, btnW, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("⚠ preset 초기화"), btn -> {
            MusixClient.config().resetSettingsToDefaults();
            KeyBindings.resetPreset(currentPreset());
            awaitingIndex = -1;
            awaitingSecondary = false;
        }).dimensions(startX + (btnW + gap) * 3, by, btnW, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("닫기"), btn -> this.close())
                .dimensions(startX + (btnW + gap) * 4, by, btnW, 20).build());

        // v5.1.0: 상단 이조 컨트롤 — [-12][-1] (값) [+1][+12]. 메뉴에서만 조절.
        transposeRowY = 32;
        int th = 14, twS = 22, twL = 28, tg = 3, valW = 96;
        int tTotal = twL + tg + twS + tg + valW + tg + twS + tg + twL;
        int tx2 = (this.width - tTotal) / 2;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("-12"), b -> adjustTranspose(-12))
                .dimensions(tx2, transposeRowY, twL, th).build()); tx2 += twL + tg;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("-1"), b -> adjustTranspose(-1))
                .dimensions(tx2, transposeRowY, twS, th).build()); tx2 += twS + tg;
        transposeTextX = tx2 + valW / 2; tx2 += valW + tg;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("+1"), b -> adjustTranspose(1))
                .dimensions(tx2, transposeRowY, twS, th).build()); tx2 += twS + tg;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("+12"), b -> adjustTranspose(12))
                .dimensions(tx2, transposeRowY, twL, th).build());
    }

    /** v5.1.0: 이조값 조절 (DB 즉시 저장). */
    private void adjustTranspose(int delta) {
        MusixConfig cfg = MusixClient.config();
        if (cfg != null) cfg.setTransposeSemitones(cfg.transposeSemitones + delta);
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

        context.drawCenteredTextWithShadow(tr, "♪ Musix ♪", cx, 8, COLOR_TITLE);
        context.drawCenteredTextWithShadow(tr, "v" + MusixClient.version(), cx, 20, COLOR_VERSION);

        // v4.1.0: 상태 박스 완전 제거 → MusixStatsScreen 으로 이동
        // 자동 매핑은 advanced 화면 또는 자동매핑 기능으로 대체. 모든 row Y 비활성.
        rowYClickButton = -1;
        rowYClickAction = -1;
        rowYDebug = -1;
        rowYAutoMap = -1;

        MusixConfig cfg = MusixClient.config();
        // v5.1.0: 상단 이조 값 표시 ([-12][-1] (값) [+1][+12] 버튼은 init() 에서 추가)
        int tsv = cfg.transposeSemitones;
        String tLabel = tsv == 0 ? "이조: 0 (원음)" : "이조: " + (tsv > 0 ? "+" : "") + tsv + " 반음";
        context.drawCenteredTextWithShadow(tr, tLabel, transposeTextX, transposeRowY + 3,
                tsv == 0 ? COLOR_VERSION : COLOR_OK);
        String active = KeyBindings.activePresetName();
        String shown = currentPreset();

        // ===== preset 탭 UI (상태 박스 자리 없어져서 키 매핑 표가 위로) =====
        listX = 20;
        listW = this.width - 40;
        int tabsY = 52; // v5.1.0: 상단 이조 컨트롤 줄(32) 공간 확보로 아래로 이동
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

        // v5.3.0: 음량 탭은 상자 슬롯/사용 횟수 개념이 없어 컬럼 구성이 다르다
        boolean volumeTab = MusixConfig.PRESET_VOLUME.equals(shown);

        int colNote = listX + 8, colSlot = listX + 86, colMainKey = listX + 120, colSecKey = listX + 232;
        this.colMainKeyX = colMainKey;
        this.colSecKeyX = colSecKey;
        context.drawTextWithShadow(tr, volumeTab ? "동작" : "노트", colNote, colLabelY, COLOR_LABEL);
        if (!volumeTab) context.drawTextWithShadow(tr, "슬롯", colSlot, colLabelY, COLOR_LABEL);
        context.drawTextWithShadow(tr, "메인 키", colMainKey, colLabelY, COLOR_LABEL);
        context.drawTextWithShadow(tr, "보조 키", colSecKey,  colLabelY, COLOR_LABEL);
        String countHeader = volumeTab ? ("현재 음량 " + VolumeController.current()) : "사용";
        context.drawTextWithShadow(tr, countHeader, listX + listW - tr.getWidth(countHeader) - 10,
                colLabelY, volumeTab ? COLOR_OK : COLOR_LABEL);

        context.fill(listX, listTop, listX + listW, listBottom, COLOR_BG);
        drawBorder(context, listX, listTop, listW, listBottom - listTop);

        long now = System.currentTimeMillis();
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
                if (!volumeTab) {
                    context.drawTextWithShadow(tr, "#" + note.mapping().slot, colSlot, rowY, COLOR_VERSION);
                }

                boolean rowConflict = conflictActive && rowIndex == conflictIndex;
                // 메인 키 칸
                if (rowIndex == awaitingIndex && !awaitingSecondary) {
                    context.drawTextWithShadow(tr, "▶ 키 입력", colMainKey, rowY, COLOR_AWAITING);
                } else {
                    int keyColor = note.isUnbound() ? COLOR_WARN : (rowConflict ? COLOR_WARN : COLOR_VALUE);
                    context.drawTextWithShadow(tr, note.displayKey(), colMainKey, rowY, keyColor);
                }
                // 보조 키 칸 (미설정 = 회색 "-")
                if (rowIndex == awaitingIndex && awaitingSecondary) {
                    context.drawTextWithShadow(tr, "▶ 키 입력", colSecKey, rowY, COLOR_AWAITING);
                } else {
                    int secColor = note.isSecondaryUnbound() ? COLOR_VERSION : (rowConflict ? COLOR_WARN : COLOR_VALUE);
                    context.drawTextWithShadow(tr, note.displaySecondaryKey(), colSecKey, rowY, secColor);
                }
                // 사용 횟수 표시
                int cnt = MusixStatus.countOf(note.mapping().note);
                if (cnt > 0) {
                    String cntStr = "× " + cnt;
                    context.drawTextWithShadow(tr, cntStr, listX + listW - tr.getWidth(cntStr) - 10,
                            rowY, COLOR_OK);
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
            help = awaitingSecondary
                    ? "▶ 보조 키를 누르세요 (Shift/Ctrl/Alt/Win/Space/Tab/CapsLock/Enter/\\/Backspace 조합 가능). ESC=해제"
                    : "▶ 메인 키를 누르세요 (Shift/Ctrl/Alt/Win/Space/Tab/CapsLock/Enter/\\/Backspace 조합 가능). ESC=기본값";
            helpColor = COLOR_AWAITING;
        } else if (volumeTab) {
            help = "방향키 = 음량 1↔10 순환 · Tab+숫자 = 직접 지정 · GUI 밖 좌클릭 ↑ / 우클릭 ↓";
            helpColor = COLOR_VERSION;
        } else {
            help = "Musix 상자에서 키 누르면 음 재생 (상자 제목별로 preset 자동 선택)";
            helpColor = COLOR_VERSION;
        }
        if (volumeTab && awaitingIndex < 0 && !conflictActive) {
            context.drawCenteredTextWithShadow(tr,
                    "악기 상자를 열면 음량 " + MusixConfig.VOLUME_DEFAULT + " 로 맞춰집니다. "
                            + "9~10은 음량이 아니라 들리는 거리가 늘어납니다.",
                    cx, this.height - 62, COLOR_LABEL);
        }
        context.drawCenteredTextWithShadow(tr, help, cx, this.height - 50, helpColor);
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
                    awaitingSecondary = false;
                    return true;
                }
            }
        }
        // v4.0.4: 클릭 버튼/동작 라인 메뉴에서 제거 (rowY = -1 이면 작동 안 함)
        if (rowYClickButton >= 0 && mouseY >= rowYClickButton && mouseY < rowYClickButton + ROW_HEIGHT) {
            MusixConfig cfg = MusixClient.config();
            int next = (cfg.clickButton + (button == 1 ? CLICK_BUTTONS.length - 1 : 1)) % CLICK_BUTTONS.length;
            cfg.setClickButton(next);
            return true;
        }
        if (rowYClickAction >= 0 && mouseY >= rowYClickAction && mouseY < rowYClickAction + ROW_HEIGHT) {
            MusixConfig cfg = MusixClient.config();
            int idx = indexOf(CLICK_ACTIONS, cfg.clickAction);
            int next = (idx + (button == 1 ? CLICK_ACTIONS.length - 1 : 1)) % CLICK_ACTIONS.length;
            cfg.setClickAction(CLICK_ACTIONS[next]);
            return true;
        }
        // 디버그 라인은 고급 설정 화면에서만
        // v5.3.0: rowYAutoMap 이 -1 (비활성) 일 때 화면 최상단 클릭이 자동매핑을 실행하던 버그 수정
        if (rowYAutoMap >= 0 && mouseY >= rowYAutoMap && mouseY < rowYAutoMap + ROW_HEIGHT) {
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
                    boolean sec = mouseX >= colSecKeyX - 4; // 보조 키 칸 영역 (그 왼쪽은 메인)
                    if (button == 1) {
                        // 우클릭 = 해당 칸 해제
                        KeyBindings.NoteEntry n = KeyBindings.getNote(currentPreset(), row);
                        if (n != null) {
                            if (sec) n.setSecondaryKey(InputUtil.UNKNOWN_KEY, 0);
                            else     n.setKey(InputUtil.UNKNOWN_KEY, 0);
                        }
                        awaitingIndex = -1;
                        awaitingSecondary = false;
                    } else {
                        awaitingIndex = row;
                        awaitingSecondary = sec;
                    }
                    return true;
                }
            }
            awaitingIndex = -1;
            awaitingSecondary = false;
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
    //? if >=1.20.2 {
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double amount) {
    //?} else
    /*public boolean mouseScrolled(double mouseX, double mouseY, double amount) {*/
        if (mouseY >= listTop && mouseY <= listBottom) {
            scroll -= (int) (amount * ROW_HEIGHT * 2);
            clampScroll();
            return true;
        }
        //? if >=1.20.2 {
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, amount);
        //?} else
        /*return super.mouseScrolled(mouseX, mouseY, amount);*/
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
                awaitingSecondary = false;
                return true;
            }
            // v5.3.0: 조합키로 쓰이는 키(Shift/Ctrl/Alt/Win/Space/Tab/CapsLock/Enter/\/Backspace)는
            // 단독 매핑 불가 — 사용자가 Tab+1 처럼 조합을 완성할 때까지 대기
            if (KeyBindings.isModifierKey(keyCode)) return true;

            // v3.12.0: 눌려있는 조합키 비트를 mods 에 덧붙임
            int effectiveMods = KeyBindings.augmentMods(modifiers);
            int mods = effectiveMods & KeyBindings.MOD_MASK;
            if (hasConflict(awaitingIndex, keyCode, scanCode, mods)) return true;
            InputUtil.Key key = InputUtil.fromKeyCode(keyCode, scanCode);
            applyKeyChange(awaitingIndex, key, mods);
            awaitingIndex = -1;
            awaitingSecondary = false;
            return true;
        }
        KeyBinding menu = KeyBindings.getMenuBinding();
        if (menu != null && menu.matchesKey(keyCode, scanCode)) {
            this.close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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
        // v4.2.0: 자기 음의 반대 칸과 충돌 거부 (같은 음 메인=보조 동일 키 방지)
        KeyBindings.NoteEntry self = KeyBindings.getNote(currentPreset(), ownIndex);
        if (self != null) {
            boolean clashSelf = awaitingSecondary
                    ? self.matchesMain(keyCode, scanCode, modifiers)
                    : self.matchesSecondary(keyCode, scanCode, modifiers);
            if (clashSelf) {
                conflictIndex = ownIndex;
                conflictUntil = System.currentTimeMillis() + CONFLICT_FLASH_MS;
                return true;
            }
        }
        return false;
    }

    private void tryResetToDefault(int index) {
        KeyBindings.NoteEntry note = KeyBindings.getNote(currentPreset(), index);
        if (note == null) return;
        // v4.2.0: 보조 키 편집 중 ESC → 보조 키만 해제 (메인 보존)
        if (awaitingSecondary) {
            note.setSecondaryKey(InputUtil.UNKNOWN_KEY, 0);
            return;
        }
        String defaultTk = MusixConfig.lookupDefaultKeyForSlot(note.mapping().preset, note.mapping().slot);
        if (defaultTk == null) return;
        InputUtil.Key defaultKey;
        try {
            defaultKey = defaultTk.isEmpty() ? InputUtil.UNKNOWN_KEY : InputUtil.fromTranslationKey(defaultTk);
        } catch (IllegalArgumentException e) { return; }
        int kc = defaultKey.getCategory() == InputUtil.Type.KEYSYM ? defaultKey.getCode() : GLFW.GLFW_KEY_UNKNOWN;
        int sc = defaultKey.getCategory() == InputUtil.Type.SCANCODE ? defaultKey.getCode() : 0;
        int defaultMods = MusixConfig.lookupDefaultModifierForSlot(note.mapping().preset, note.mapping().slot);
        if (hasConflict(index, kc, sc, defaultMods)) return;
        note.setKey(defaultKey, defaultMods); // v4.2.0: 메인 키만 복원 (보조 키 보존)
    }

    private void applyKeyChange(int index, InputUtil.Key key, int modifiers) {
        KeyBindings.NoteEntry note = KeyBindings.getNote(currentPreset(), index);
        if (note == null) return;
        if (awaitingSecondary) note.setSecondaryKey(key, modifiers);
        else                   note.setKey(key, modifiers);
    }

    @Override
    public void close() {
        awaitingIndex = -1;
        awaitingSecondary = false;
        if (this.client != null) this.client.setScreen(this.parent);
    }

    @Override
    public boolean shouldPause() { return false; }
}

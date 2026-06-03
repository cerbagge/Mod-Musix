package com.musix.input;

import com.musix.MusixClient;
import com.musix.config.MusixConfig;
import com.musix.config.MusixStatus;
import com.musix.key.KeyBindings;
import com.musix.util.DebugChat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Transmitter;
import java.util.ArrayList;
import java.util.List;

/**
 * v4.0.0: MIDI 키보드/컨트롤러 입력 수신 → 슬롯 클릭.
 * - javax.sound.midi 표준 사용 (외부 의존성 0)
 * - 메인 스레드 안전: client.execute() 로 큐잉
 * - 범위 밖 노트 (F#2~F#6 외) 는 무시
 * - 채널 10 (channel 9, 0-indexed) = 드럼 (현재 미구현, 향후 확장)
 */
public final class MusixMidi {
    private static final Logger LOG = LoggerFactory.getLogger("musix/midi");

    /** F#2 = MIDI 42, F#6 = MIDI 90. 모드의 49음 범위. */
    public static final int MIN_MIDI_NOTE = 42;
    public static final int MAX_MIDI_NOTE = 90;

    private static MidiDevice connectedDevice;
    private static Transmitter transmitter;
    private static Receiver receiver;

    private MusixMidi() {}

    /** 입력용(Transmitter 보유) MIDI 장치 목록. */
    public static List<MidiDevice.Info> scanInputDevices() {
        List<MidiDevice.Info> result = new ArrayList<>();
        for (MidiDevice.Info info : MidiSystem.getMidiDeviceInfo()) {
            try {
                MidiDevice dev = MidiSystem.getMidiDevice(info);
                // getMaxTransmitters() = -1 → 무제한, 0 = 입력 불가
                if (dev.getMaxTransmitters() != 0) result.add(info);
            } catch (Exception ignored) {}
        }
        return result;
    }

    public static boolean connect(String deviceName) {
        if (deviceName == null || deviceName.isEmpty()) return false;
        disconnect();
        for (MidiDevice.Info info : scanInputDevices()) {
            if (!info.getName().equals(deviceName)) continue;
            try {
                MidiDevice dev = MidiSystem.getMidiDevice(info);
                dev.open();
                Transmitter t = dev.getTransmitter();
                Receiver r = new MusixMidiReceiver();
                t.setReceiver(r);
                connectedDevice = dev;
                transmitter = t;
                receiver = r;
                LOG.info("[MIDI] 연결됨: {}", deviceName);
                return true;
            } catch (Exception e) {
                LOG.error("[MIDI] 연결 실패 ({}): {}", deviceName, e.getMessage());
                disconnect();
                return false;
            }
        }
        LOG.warn("[MIDI] 장치 없음: {}", deviceName);
        return false;
    }

    public static void disconnect() {
        try { if (transmitter != null) transmitter.close(); } catch (Exception ignored) {}
        try { if (receiver != null) receiver.close(); } catch (Exception ignored) {}
        try { if (connectedDevice != null) connectedDevice.close(); } catch (Exception ignored) {}
        transmitter = null;
        receiver = null;
        connectedDevice = null;
    }

    public static boolean isConnected() {
        return connectedDevice != null && connectedDevice.isOpen();
    }

    public static String connectedDeviceName() {
        if (connectedDevice == null) return null;
        return connectedDevice.getDeviceInfo().getName();
    }

    /** MIDI note → 음 이름 (예: 60 = "C4"). 범위 밖이면 null. */
    public static String midiNoteToName(int midiNote) {
        if (midiNote < MIN_MIDI_NOTE || midiNote > MAX_MIDI_NOTE) return null;
        String[] names = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
        int octave = (midiNote / 12) - 1;
        int noteIdx = midiNote % 12;
        return names[noteIdx] + octave;
    }

    /** 메인 스레드에서 NoteOn 처리. MIDI 콜백 스레드에서 client.execute() 로 전달됨. */
    static void processNoteOn(int channel, int midiNote, int velocity) {
        MusixConfig cfg = MusixClient.config();
        if (cfg == null) { LOG.warn("[MIDI] config null"); return; }
        if (!cfg.midiEnabled) {
            if (cfg.debugMode) DebugChat.warn("[MIDI] midiEnabled OFF — note=" + midiNote);
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) { LOG.warn("[MIDI] client null"); return; }
        if (client.player == null) {
            if (cfg.debugMode) DebugChat.warn("[MIDI] player 없음 (서버 연결 안 됨?)");
            return;
        }
        if (client.interactionManager == null) {
            if (cfg.debugMode) DebugChat.warn("[MIDI] interactionManager 없음");
            return;
        }
        if (!(client.currentScreen instanceof GenericContainerScreen gcs)) {
            if (cfg.debugMode) {
                String s = client.currentScreen == null ? "null"
                        : client.currentScreen.getClass().getSimpleName();
                DebugChat.warn("[MIDI] 악기 상자 안 열림 (현재: " + s + ") — note=" + midiNote);
            }
            return;
        }
        Text title = gcs.getTitle();
        if (title == null) {
            if (cfg.debugMode) DebugChat.warn("[MIDI] 상자 제목 null");
            return;
        }
        String titleStr = title.getString();
        if (!cfg.titleMatchesPrefix(titleStr)) {
            if (cfg.debugMode) DebugChat.warn("[MIDI] 상자 제목 매칭 실패: '" + titleStr + "'");
            return;
        }

        String preset = cfg.activePresetForTitle(titleStr);
        // v5.1.0: 이조 적용 — 범위 체크 전에 반음 오프셋을 더한다.
        int playedNote = midiNote + cfg.transposeSemitones;
        String noteName = midiNoteToName(playedNote);
        if (noteName == null) {
            if (cfg.debugMode) {
                String ts = cfg.transposeSemitones != 0 ? "→" + playedNote : "";
                DebugChat.warn("[MIDI] 범위 밖 노트 " + midiNote + ts + " 무시 (F#2~F#6 만)");
            }
            return;
        }

        // 활성 preset 의 음들 중 매칭 찾기
        List<KeyBindings.NoteEntry> notes = KeyBindings.getNotes(preset);
        if (notes.isEmpty()) {
            if (cfg.debugMode) DebugChat.warn("[MIDI] preset '" + preset + "' 매핑 비어있음");
            return;
        }
        for (KeyBindings.NoteEntry note : notes) {
            if (!noteName.equals(note.mapping().note)) continue;
            int slot = note.mapping().slot;
            GenericContainerScreenHandler handler = gcs.getScreenHandler();
            if (slot < 0 || slot >= handler.slots.size()) {
                if (cfg.debugMode) DebugChat.warn("[MIDI] 슬롯 인덱스 범위 밖: " + slot);
                return;
            }
            if (MusixConfig.BLOCKED_SLOTS.contains(slot)) {
                if (cfg.debugMode) DebugChat.warn("[MIDI] 차단 슬롯 " + slot + " — 스킵");
                return;
            }
            client.interactionManager.clickSlot(handler.syncId, slot, cfg.clickButton,
                    parseAction(cfg.clickAction), client.player);
            MusixStatus.recordNote(noteName, slot);
            if (cfg.debugMode) {
                String ts = cfg.transposeSemitones != 0 ? "→" + playedNote : "";
                DebugChat.ok("[MIDI] note=" + midiNote + ts + "(" + noteName + ") vel=" + velocity
                        + " → slot=" + slot);
            }
            return;
        }
        if (cfg.debugMode) DebugChat.warn("[MIDI] 매핑 없음: '" + noteName + "' (MIDI " + midiNote
                + ", preset='" + preset + "', " + notes.size() + "음 등록됨)");
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

    /** MIDI 수신 — 별도 스레드에서 호출됨. 메인 스레드로 즉시 전달. */
    private static class MusixMidiReceiver implements Receiver {
        @Override
        public void send(MidiMessage message, long timeStamp) {
            if (!(message instanceof ShortMessage sm)) return;
            int cmd = sm.getCommand();
            int ch = sm.getChannel();
            int note = sm.getData1();
            int vel = sm.getData2();

            // v4.0.2: 디버그 모드일 때 모든 NoteOn/NoteOff raw 로그 — 신호 도달 확인용
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && (cmd == ShortMessage.NOTE_ON || cmd == ShortMessage.NOTE_OFF)) {
                client.execute(() -> {
                    MusixConfig cfg = MusixClient.config();
                    if (cfg != null && cfg.debugMode) {
                        String t = (cmd == ShortMessage.NOTE_ON && vel > 0) ? "ON " : "OFF";
                        DebugChat.info("[MIDI-RAW] " + t + " ch=" + ch + " note=" + note + " vel=" + vel);
                    }
                });
            }

            // NoteOn + velocity > 0 만 처리 (velocity 0 인 NoteOn = NoteOff 의 일반적 패턴)
            if (cmd == ShortMessage.NOTE_ON && vel > 0) {
                if (client != null) {
                    client.execute(() -> processNoteOn(ch, note, vel));
                }
            }
        }
        @Override public void close() {}
    }
}

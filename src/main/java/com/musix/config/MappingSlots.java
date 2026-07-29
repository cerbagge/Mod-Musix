package com.musix.config;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.musix.MusixClient;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * v3.10.0: 키 매핑 슬롯(이름 붙은 매핑 백업) 관리.
 * - 저장 위치: <config>/musix-slots/<이름>.json
 * - JSON 안에 preset 별 매핑 + 클릭 설정 포함 (export 형식 호환)
 * - 외부 musix-export-*.json 가져오기 지원
 */
public final class MappingSlots {
    private static final Logger LOG = LoggerFactory.getLogger("musix/slots");
    private static final String SLOT_DIR_NAME = "musix-slots";

    private MappingSlots() {}

    /**
     * v5.5.0: 자동 매핑이 매핑을 덮어쓰기 직전에 남기는 되돌리기용 슬롯 이름.
     * 매핑 슬롯 화면 목록에 그대로 보이므로, 오염되면 [불러오기] 한 번으로 복구된다.
     */
    public static final String AUTO_BACKUP_NAME = "직전자동매핑-백업";

    /**
     * v5.5.0: 자동 매핑 적용 직전 스냅샷. 항상 같은 이름을 써서 1단계만 보관한다.
     * 실패해도 자동 매핑 자체를 막지는 않는다 (백업은 부가 기능).
     */
    public static void saveAutoBackup(MusixConfig cfg) {
        if (cfg == null) return;
        try {
            JsonObject root = buildJson(AUTO_BACKUP_NAME, cfg);
            String json = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root);
            Files.writeString(filePathFor(AUTO_BACKUP_NAME), json);
            LOG.info("[Musix] 자동 매핑 직전 스냅샷 저장");
        } catch (IOException e) {
            LOG.warn("[Musix] 스냅샷 저장 실패 (자동 매핑은 계속): {}", e.getMessage());
        }
    }

    public static Path slotDir() {
        Path p = FabricLoader.getInstance().getConfigDir().resolve(SLOT_DIR_NAME);
        try { Files.createDirectories(p); } catch (IOException ignored) {}
        return p;
    }

    /** 저장된 슬롯 이름 목록 (정렬됨). */
    public static List<String> listNames() {
        List<String> result = new ArrayList<>();
        Path dir = slotDir();
        if (!Files.isDirectory(dir)) return result;
        try (Stream<Path> s = Files.list(dir)) {
            s.filter(p -> p.toString().endsWith(".json"))
                    .map(p -> p.getFileName().toString().replaceFirst("\\.json$", ""))
                    .sorted()
                    .forEach(result::add);
        } catch (IOException e) {
            LOG.error("[Musix] 슬롯 목록 조회 실패: {}", e.getMessage());
        }
        return result;
    }

    /** 슬롯 파일이 저장된 시각 (JSON 내 savedAt). 없거나 실패 시 빈 문자열. */
    public static String savedAt(String name) {
        try {
            String json = Files.readString(filePathFor(name));
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has("savedAt")) return root.get("savedAt").getAsString();
        } catch (Exception ignored) {}
        return "";
    }

    public static boolean exists(String name) {
        return Files.exists(filePathFor(name));
    }

    /** 현재 활성 매핑을 이름으로 저장. */
    public static boolean save(String name, MusixConfig cfg) {
        if (!isValidName(name)) {
            LOG.warn("[Musix] 슬롯 저장: 이름 형식 오류 '{}'", name);
            return false;
        }
        try {
            JsonObject root = buildJson(name, cfg);
            String json = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root);
            Files.writeString(filePathFor(name), json);
            LOG.info("[Musix] 매핑 슬롯 저장: '{}'", name);
            return true;
        } catch (IOException e) {
            LOG.error("[Musix] 슬롯 저장 실패 ('{}'): {}", name, e.getMessage());
            return false;
        }
    }

    /** 슬롯에서 매핑 불러와 현재 DB 에 적용. 호출 후 MusixClient.reloadConfig() 권장. */
    public static boolean load(String name) {
        Path file = filePathFor(name);
        if (!Files.exists(file)) {
            LOG.warn("[Musix] 슬롯 없음: '{}'", name);
            return false;
        }
        try {
            String json = Files.readString(file);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            applyJsonToDatabase(root);
            LOG.info("[Musix] 매핑 슬롯 불러옴: '{}'", name);
            return true;
        } catch (Exception e) {
            LOG.error("[Musix] 슬롯 불러오기 실패 ('{}'): {}", name, e.getMessage());
            return false;
        }
    }

    public static boolean delete(String name) {
        try {
            return Files.deleteIfExists(filePathFor(name));
        } catch (IOException e) {
            LOG.error("[Musix] 슬롯 삭제 실패 ('{}'): {}", name, e.getMessage());
            return false;
        }
    }

    /** 슬롯 → 외부 폴더로 내보내기 (config 폴더에 timestamp jar 와 별도로 export). */
    public static Path exportToConfigDir(String name) {
        try {
            Path src = filePathFor(name);
            if (!Files.exists(src)) return null;
            String ts = LocalDateTime.now().toString().replaceAll("[:T.]", "-");
            Path dst = FabricLoader.getInstance().getConfigDir()
                    .resolve("musix-export-" + sanitize(name) + "-" + ts.substring(0, 19) + ".json");
            Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
            return dst;
        } catch (IOException e) {
            LOG.error("[Musix] 내보내기 실패: {}", e.getMessage());
            return null;
        }
    }

    /** config 폴더의 musix-export-*.json 파일 목록 (최신순). */
    public static List<Path> findImportableFiles() {
        List<Path> result = new ArrayList<>();
        Path cfg = FabricLoader.getInstance().getConfigDir();
        try (Stream<Path> s = Files.list(cfg)) {
            s.filter(p -> {
                String n = p.getFileName().toString();
                return n.startsWith("musix-export-") && n.endsWith(".json");
            }).sorted(Comparator.reverseOrder()).forEach(result::add);
        } catch (IOException e) {
            LOG.error("[Musix] import 파일 검색 실패: {}", e.getMessage());
        }
        return result;
    }

    /** 외부 JSON 파일을 슬롯으로 import. slotName 으로 저장. */
    public static boolean importFile(Path src, String slotName) {
        if (!isValidName(slotName)) return false;
        if (!Files.exists(src)) return false;
        try {
            // JSON 파싱 검증
            String json = Files.readString(src);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (!root.has("presets")) {
                LOG.warn("[Musix] import: presets 필드 없음 — {}", src);
                return false;
            }
            // savedAt 갱신, name 기록
            root.addProperty("name", slotName);
            root.addProperty("savedAt", LocalDateTime.now().toString());
            String pretty = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root);
            Files.writeString(filePathFor(slotName), pretty);
            LOG.info("[Musix] import 성공: '{}' ← {}", slotName, src.getFileName());
            return true;
        } catch (Exception e) {
            LOG.error("[Musix] import 실패 ({}): {}", src.getFileName(), e.getMessage());
            return false;
        }
    }

    // === 내부 헬퍼 ===

    private static Path filePathFor(String name) {
        return slotDir().resolve(sanitize(name) + ".json");
    }

    private static JsonObject buildJson(String name, MusixConfig cfg) {
        JsonObject root = new JsonObject();
        root.addProperty("name", name);
        root.addProperty("modVersion", MusixClient.version());
        root.addProperty("savedAt", LocalDateTime.now().toString());
        root.addProperty("savedOS", MusixClient.osName()); // v3.10.4: OS 정보
        root.addProperty("containerPrefix", cfg.containerPrefix);
        root.addProperty("clickButton", cfg.clickButton);
        root.addProperty("clickAction", cfg.clickAction);
        root.addProperty("midiEnabled", cfg.midiEnabled);
        root.addProperty("midiDeviceName", cfg.midiDeviceName == null ? "" : cfg.midiDeviceName);

        JsonObject presets = new JsonObject();
        for (String preset : MusixConfig.ALL_PRESETS) {
            JsonArray arr = new JsonArray();
            for (KeyMapping m : cfg.mappingsFor(preset)) {
                JsonObject o = new JsonObject();
                o.addProperty("note", m.note);
                o.addProperty("slot", m.slot);
                o.addProperty("key", m.defaultKey == null ? "" : m.defaultKey);
                o.addProperty("modifiers", m.modifiers);
                o.addProperty("secondaryKey", m.secondaryKey == null ? "" : m.secondaryKey);
                o.addProperty("secondaryModifiers", m.secondaryModifiers);
                arr.add(o);
            }
            presets.add(preset, arr);
        }
        root.add("presets", presets);
        return root;
    }

    private static void applyJsonToDatabase(JsonObject root) {
        MusixDatabase db = MusixDatabase.get();
        // 설정 복원 (있는 키만)
        if (root.has("containerPrefix"))
            db.setSetting("containerPrefix", root.get("containerPrefix").getAsString());
        if (root.has("clickButton"))
            db.setSetting("clickButton", root.get("clickButton").getAsString());
        if (root.has("clickAction"))
            db.setSetting("clickAction", root.get("clickAction").getAsString());

        if (!root.has("presets")) return;
        JsonObject presets = root.getAsJsonObject("presets");
        for (String preset : presets.keySet()) {
            JsonElement el = presets.get(preset);
            if (!el.isJsonArray()) continue;
            JsonArray arr = el.getAsJsonArray();
            List<MusixDatabase.MappingRow> rows = new ArrayList<>();
            int rejected = 0;
            for (JsonElement e : arr) {
                if (!e.isJsonObject()) continue;
                JsonObject o = e.getAsJsonObject();
                if (!o.has("slot") || !o.has("note")) continue;
                int slot = o.get("slot").getAsInt();
                // v5.4.1: 외부 JSON 은 신뢰할 수 없다. 범위 밖 슬롯은 버린다.
                // 검증이 없으면 "slot": 60 같은 값이 그대로 들어가 플레이어 인벤토리가 클릭된다.
                if (!MusixConfig.isSlotInRange(preset, slot)) {
                    rejected++;
                    continue;
                }
                String note = o.get("note").getAsString();
                String key = o.has("key") ? o.get("key").getAsString() : "";
                int mods = o.has("modifiers") ? o.get("modifiers").getAsInt() : 0;
                // v4.2.0: 보조 키 (구버전 JSON 엔 없음 → unbound)
                String key2 = o.has("secondaryKey") ? o.get("secondaryKey").getAsString() : "";
                int mods2 = o.has("secondaryModifiers") ? o.get("secondaryModifiers").getAsInt() : 0;
                rows.add(new MusixDatabase.MappingRow(slot, note, key, mods, key2, mods2));
            }
            if (rejected > 0) {
                LOG.warn("[Musix] preset '{}': 범위 밖 슬롯 {}개 거부 (허용 0~{})",
                        preset, rejected, MusixConfig.maxSlotFor(preset));
            }
            if (!rows.isEmpty()) db.replaceMappings(preset, rows);
        }
    }

    public static boolean isValidName(String name) {
        if (name == null) return false;
        String t = name.trim();
        if (t.isEmpty() || t.length() > 32) return false;
        if (t.contains("/") || t.contains("\\") || t.contains("..")) return false;
        return true;
    }

    /** 파일명 안전 문자만 남김. 한글/영문/숫자/공백/대시/언더스코어 허용. */
    private static String sanitize(String name) {
        return name.trim().replaceAll("[^a-zA-Z0-9가-힣_\\- ]", "_");
    }
}

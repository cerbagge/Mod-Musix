package com.musix.config;

import net.fabricmc.loader.api.FabricLoader;
import org.h2.jdbcx.JdbcDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class MusixDatabase {
    private static final Logger LOG = LoggerFactory.getLogger("musix/db");
    private static MusixDatabase INSTANCE;

    private final JdbcDataSource ds;

    private MusixDatabase(Path dbFile) {
        ds = new JdbcDataSource();
        String url = "jdbc:h2:file:" + dbFile.toAbsolutePath().toString().replace('\\', '/')
                + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL;CASE_INSENSITIVE_IDENTIFIERS=TRUE";
        ds.setURL(url);
        ds.setUser("musix");
        ds.setPassword("");
    }

    public static MusixDatabase get() {
        if (INSTANCE == null) {
            Path configDir = FabricLoader.getInstance().getConfigDir();
            try { Files.createDirectories(configDir); } catch (Exception ignored) {}
            Path dbBase = configDir.resolve("musix-data");
            INSTANCE = new MusixDatabase(dbBase);
            if (!INSTANCE.init()) {
                quarantineCorruptedDb(configDir);
                INSTANCE = new MusixDatabase(dbBase);
                if (!INSTANCE.init()) LOG.error("[Musix] DB 재초기화 실패");
            }
        }
        return INSTANCE;
    }

    private static void quarantineCorruptedDb(Path configDir) {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        for (String name : new String[]{"musix-data.mv.db", "musix-data.trace.db", "musix-data.lock.db"}) {
            Path src = configDir.resolve(name);
            if (!Files.exists(src)) continue;
            try {
                Files.move(src, configDir.resolve(name + ".broken-" + ts), StandardCopyOption.REPLACE_EXISTING);
                LOG.warn("[Musix] 손상 DB 격리: {}", name);
            } catch (IOException e) { LOG.error("[Musix] 격리 실패: {}", e.getMessage()); }
        }
    }

    public Connection conn() throws SQLException { return ds.getConnection(); }

    private boolean init() {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS settings ("
                    + "k VARCHAR(64) PRIMARY KEY, v VARCHAR(512))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS mappings ("
                    + "preset VARCHAR(32) NOT NULL, "
                    + "slot INT NOT NULL, "
                    + "note VARCHAR(32) NOT NULL, "
                    + "key_name VARCHAR(128) NOT NULL DEFAULT '', "
                    + "modifiers INT NOT NULL DEFAULT 0, "
                    + "PRIMARY KEY (preset, slot))");
            return true;
        } catch (SQLException e) {
            LOG.error("[Musix] DB init 실패: {}", e.getMessage());
            return false;
        }
    }

    public String getSetting(String key, String defaultValue) {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement("SELECT v FROM settings WHERE k = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getString(1); }
        } catch (SQLException e) {}
        return defaultValue;
    }

    public void setSetting(String key, String value) {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                     "MERGE INTO settings (k, v) KEY (k) VALUES (?, ?)")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        } catch (SQLException e) {}
    }

    public List<MappingRow> getAllMappings(String preset) {
        List<MappingRow> list = new ArrayList<>();
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT slot, note, key_name, modifiers FROM mappings WHERE preset = ? ORDER BY slot")) {
            ps.setString(1, preset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    list.add(new MappingRow(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getInt(4)));
            }
        } catch (SQLException e) {}
        return list;
    }

    public void upsertMapping(String preset, int slot, String note, String keyName, int modifiers) {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                     "MERGE INTO mappings (preset, slot, note, key_name, modifiers) KEY (preset, slot) VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, preset);
            ps.setInt(2, slot);
            ps.setString(3, note);
            ps.setString(4, keyName == null ? "" : keyName);
            ps.setInt(5, modifiers);
            ps.executeUpdate();
        } catch (SQLException e) {}
    }

    public void updateMappingKey(String preset, int slot, String keyName, int modifiers) {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE mappings SET key_name = ?, modifiers = ? WHERE preset = ? AND slot = ?")) {
            ps.setString(1, keyName == null ? "" : keyName);
            ps.setInt(2, modifiers);
            ps.setString(3, preset);
            ps.setInt(4, slot);
            ps.executeUpdate();
        } catch (SQLException e) {}
    }

    public void replaceMappings(String preset, List<MappingRow> rows) {
        try (Connection c = conn()) {
            c.setAutoCommit(false);
            try (PreparedStatement del = c.prepareStatement("DELETE FROM mappings WHERE preset = ?")) {
                del.setString(1, preset);
                del.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO mappings (preset, slot, note, key_name, modifiers) VALUES (?, ?, ?, ?, ?)")) {
                for (MappingRow r : rows) {
                    ps.setString(1, preset);
                    ps.setInt(2, r.slot());
                    ps.setString(3, r.note());
                    ps.setString(4, r.keyName() == null ? "" : r.keyName());
                    ps.setInt(5, r.modifiers());
                    ps.executeUpdate();
                }
            }
            c.commit();
        } catch (SQLException e) { LOG.error("[Musix] replaceMappings 실패: {}", e.getMessage()); }
    }

    public int mappingCount() {
        try (Connection c = conn(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM mappings")) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {}
        return 0;
    }

    public int mappingCountFor(String preset) {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM mappings WHERE preset = ?")) {
            ps.setString(1, preset);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getInt(1); }
        } catch (SQLException e) {}
        return 0;
    }

    public record MappingRow(int slot, String note, String keyName, int modifiers) {}
}

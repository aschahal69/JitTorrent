package com.jittorrent.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class SettingsDao {

    public Optional<String> get(Connection c, String key) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT setting_value FROM settings WHERE setting_key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getString(1)) : Optional.empty();
            }
        }
    }

    public void set(Connection c, String key, String value) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO settings (setting_key, setting_value) VALUES (?, ?) "
                        + "ON CONFLICT(setting_key) DO UPDATE SET setting_value = excluded.setting_value")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        }
    }
}
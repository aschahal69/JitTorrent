package com.jittorrent.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.jittorrent.model.FileMetadata;
import com.jittorrent.model.HistoryEntry;

public class HistoryDao {

    public void insert(Connection c, FileMetadata meta, LocalDateTime started, LocalDateTime finished,
                       String result, String detail) throws SQLException {
        String sql = "INSERT INTO download_history "
                + "(file_name, file_size, file_hash, started_at, finished_at, result, detail) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, meta.getFileName());
            ps.setLong(2, meta.getFileSize());
            ps.setString(3, meta.getFileHash());
            ps.setString(4, started.toString());
            ps.setString(5, finished.toString());
            ps.setString(6, result);
            ps.setString(7, detail);
            ps.executeUpdate();
        }
    }

    public List<HistoryEntry> findAll(Connection c) throws SQLException {
        String sql = "SELECT history_id, file_name, file_size, file_hash, started_at, finished_at, result, detail "
                + "FROM download_history ORDER BY history_id DESC";
        List<HistoryEntry> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new HistoryEntry(rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getString(4),
                        LocalDateTime.parse(rs.getString(5)), LocalDateTime.parse(rs.getString(6)),
                        rs.getString(7), rs.getString(8)));
            }
        }
        return list;
    }

    /** Returns how many rows were deleted. */
    public int deleteAll(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM download_history")) {
            return ps.executeUpdate();
        }
    }
}
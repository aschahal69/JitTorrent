package com.jittorrent.db;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.jittorrent.model.FileMetadata;
import com.jittorrent.model.LocalFile;
import com.jittorrent.model.LocalFileStatus;

public class LocalFileDao {

    /** Always store the same form of a path, or the UNIQUE constraint could not match two spellings of it. */
    private static String normalize(Path file) {
        return file.toAbsolutePath().normalize().toString();
    }

    /** INSERT, or update the status if this file at this path is already recorded. */
    public void upsert(Connection c, FileMetadata meta, Path file, LocalFileStatus status) throws SQLException {
        String sql = "INSERT INTO local_files (file_hash, file_name, file_size, save_path, status, added_at) "
                + "VALUES (?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT(file_hash, save_path) DO UPDATE SET status = excluded.status";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, meta.getFileHash());
            ps.setString(2, meta.getFileName());
            ps.setLong(3, meta.getFileSize());
            ps.setString(4, normalize(file));
            ps.setString(5, status.name());
            ps.setString(6, LocalDateTime.now().toString());
            ps.executeUpdate();
        }
    }

    /** UPDATE. Returns how many rows changed (0 means the file was never recorded). */
    public int updateStatus(Connection c, String fileHash, Path file, LocalFileStatus status) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE local_files SET status = ? WHERE file_hash = ? AND save_path = ?")) {
            ps.setString(1, status.name());
            ps.setString(2, fileHash);
            ps.setString(3, normalize(file));
            return ps.executeUpdate();
        }
    }

    /** SELECT, newest first. */
    public List<LocalFile> findAll(Connection c) throws SQLException {
        String sql = "SELECT local_id, file_hash, file_name, file_size, save_path, status, added_at "
                + "FROM local_files ORDER BY added_at DESC, local_id DESC";
        List<LocalFile> result = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new LocalFile(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getLong(4),
                        rs.getString(5), LocalFileStatus.valueOf(rs.getString(6)),
                        LocalDateTime.parse(rs.getString(7))));
            }
        }
        return result;
    }

    /** DELETE. Removes only the record, never the file on disk. */
    public boolean delete(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM local_files WHERE local_id = ?")) {
            ps.setLong(1, id);
            return ps.executeUpdate() == 1;
        }
    }
}
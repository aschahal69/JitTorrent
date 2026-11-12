package com.jittorrent.db;

import com.jittorrent.model.FileMetadata;
import com.jittorrent.model.FileSummary;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SharedFileDao {

    /** Returns true if the file was new, false if it was already registered. */
    public boolean insertFile(Connection c, FileMetadata meta) throws SQLException {
        String sql = "INSERT OR IGNORE INTO shared_files "
                + "(file_hash, file_name, file_size, piece_size, piece_count, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, meta.getFileHash());
            ps.setString(2, meta.getFileName());
            ps.setLong(3, meta.getFileSize());
            ps.setInt(4, meta.getPieceSize());
            ps.setInt(5, meta.getPieceCount());
            ps.setString(6, LocalDateTime.now().toString());
            return ps.executeUpdate() == 1;
        }
    }

    public Optional<Long> findIdByHash(Connection c, String fileHash) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT file_id FROM shared_files WHERE file_hash = ?")) {
            ps.setString(1, fileHash);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getLong(1)) : Optional.empty();
            }
        }
    }

    public void insertPieces(Connection c, long fileId, FileMetadata meta) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO file_pieces (file_id, piece_index, piece_hash) VALUES (?, ?, ?)")) {
            for (int i = 0; i < meta.getPieceCount(); i++) {
                ps.setLong(1, fileId);
                ps.setInt(2, i);
                ps.setString(3, meta.getPieceHash(i));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public List<FileSummary> listAll(Connection c) throws SQLException {
        List<FileSummary> result = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT file_name, file_size, file_hash, piece_count FROM shared_files "
                        + "ORDER BY created_at DESC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new FileSummary(rs.getString(1), rs.getLong(2), rs.getString(3), rs.getInt(4)));
            }
        }
        return result;
    }

    public Optional<FileMetadata> findMetadata(Connection c, String fileHash) throws SQLException {
        long fileId;
        String name;
        long size;
        int pieceSize;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT file_id, file_name, file_size, piece_size FROM shared_files WHERE file_hash = ?")) {
            ps.setString(1, fileHash);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                fileId = rs.getLong(1);
                name = rs.getString(2);
                size = rs.getLong(3);
                pieceSize = rs.getInt(4);
            }
        }
        List<String> hashes = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT piece_hash FROM file_pieces WHERE file_id = ? ORDER BY piece_index")) {
            ps.setLong(1, fileId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) hashes.add(rs.getString(1));
            }
        }
        return Optional.of(new FileMetadata(name, size, fileHash, pieceSize, hashes));
    }
}
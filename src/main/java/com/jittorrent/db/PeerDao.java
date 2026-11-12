package com.jittorrent.db;

import com.jittorrent.model.PeerAddress;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class PeerDao {

    /** Registers a peer if new, refreshes its last_seen if known, and returns its id. */
    public long upsertPeer(Connection c, String ip, int port) throws SQLException {
        String now = LocalDateTime.now().toString();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT OR IGNORE INTO peers (ip_address, port, last_seen) VALUES (?, ?, ?)")) {
            ps.setString(1, ip);
            ps.setInt(2, port);
            ps.setString(3, now);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE peers SET last_seen = ? WHERE ip_address = ? AND port = ?")) {
            ps.setString(1, now);
            ps.setString(2, ip);
            ps.setInt(3, port);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT peer_id FROM peers WHERE ip_address = ? AND port = ?")) {
            ps.setString(1, ip);
            ps.setInt(2, port);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    public void addSeed(Connection c, long peerId, long fileId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT OR IGNORE INTO seeds (peer_id, file_id) VALUES (?, ?)")) {
            ps.setLong(1, peerId);
            ps.setLong(2, fileId);
            ps.executeUpdate();
        }
    }

    public List<PeerAddress> findSeeders(Connection c, String fileHash) throws SQLException {
        String sql = "SELECT p.ip_address, p.port FROM peers p "
                + "JOIN seeds s ON s.peer_id = p.peer_id "
                + "JOIN shared_files f ON f.file_id = s.file_id "
                + "WHERE f.file_hash = ? ORDER BY p.peer_id";
        List<PeerAddress> result = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, fileHash);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new PeerAddress(rs.getString(1), rs.getInt(2)));
                }
            }
        }
        return result;
    }
}
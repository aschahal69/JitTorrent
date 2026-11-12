package com.jittorrent.tracker;

import com.jittorrent.db.DatabaseConnection;
import com.jittorrent.db.PeerDao;
import com.jittorrent.db.SharedFileDao;
import com.jittorrent.model.FileMetadata;
import com.jittorrent.model.FileSummary;
import com.jittorrent.model.PeerAddress;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class TrackerService {
    private final DatabaseConnection db;
    private final SharedFileDao files = new SharedFileDao();
    private final PeerDao peers = new PeerDao();

    public TrackerService(DatabaseConnection db) {
        this.db = db;
    }

    /** File + its piece hashes + the peer + the seed link: all saved together or not at all. */
    public void registerFile(FileMetadata meta, String ip, int port) throws SQLException {
        try (Connection c = db.open()) {
            c.setAutoCommit(false);
            try {
                boolean isNew = files.insertFile(c, meta);
                long fileId = files.findIdByHash(c, meta.getFileHash()).orElseThrow();
                if (isNew) {
                    files.insertPieces(c, fileId, meta);
                }
                long peerId = peers.upsertPeer(c, ip, port);
                peers.addSeed(c, peerId, fileId);
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    public List<FileSummary> listFiles() throws SQLException {
        try (Connection c = db.open()) {
            return files.listAll(c);
        }
    }

    public Optional<FileMetadata> getMetadata(String fileHash) throws SQLException {
        try (Connection c = db.open()) {
            return files.findMetadata(c, fileHash);
        }
    }

    public List<PeerAddress> getSeeders(String fileHash) throws SQLException {
        try (Connection c = db.open()) {
            return peers.findSeeders(c, fileHash);
        }
    }
}
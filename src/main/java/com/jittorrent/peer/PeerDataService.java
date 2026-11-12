package com.jittorrent.peer;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

import com.jittorrent.db.DatabaseConnection;
import com.jittorrent.db.HistoryDao;
import com.jittorrent.db.LocalFileDao;
import com.jittorrent.db.SettingsDao;
import com.jittorrent.model.FileMetadata;
import com.jittorrent.model.HistoryEntry;
import com.jittorrent.model.LocalFile;
import com.jittorrent.model.LocalFileStatus;

public class PeerDataService {
    private final DatabaseConnection db;
    private final LocalFileDao localFiles = new LocalFileDao();
    private final HistoryDao history = new HistoryDao();
    private final SettingsDao settings = new SettingsDao();

    public PeerDataService(DatabaseConnection db) {
        this.db = db;
    }

    public void recordSeed(FileMetadata meta, Path file) throws SQLException {
        try (Connection c = db.open()) {
            localFiles.upsert(c, meta, file, LocalFileStatus.SEEDING);
        }
    }

    public void beginDownload(FileMetadata meta, Path target) throws SQLException {
        try (Connection c = db.open()) {
            localFiles.upsert(c, meta, target, LocalFileStatus.DOWNLOADING);
        }
    }

    public void completeDownload(FileMetadata meta, Path target, LocalDateTime started) throws SQLException {
        finish(meta, target, started, LocalFileStatus.COMPLETE, "SUCCESS", "");
    }

    public void failDownload(FileMetadata meta, Path target, LocalDateTime started, String reason) throws SQLException {
        finish(meta, target, started, LocalFileStatus.FAILED, "FAILED",
                reason == null ? "Unknown error" : reason);
    }

    /** The status change and the history row are saved together or not at all. */
    private void finish(FileMetadata meta, Path target, LocalDateTime started,
                        LocalFileStatus status, String result, String detail) throws SQLException {
        try (Connection c = db.open()) {
            c.setAutoCommit(false);
            try {
                if (localFiles.updateStatus(c, meta.getFileHash(), target, status) == 0) {
                    localFiles.upsert(c, meta, target, status); // beginDownload was never recorded
                }
                history.insert(c, meta, started, LocalDateTime.now(), result, detail);
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    public List<LocalFile> listFiles() throws SQLException {
        try (Connection c = db.open()) {
            return localFiles.findAll(c);
        }
    }

    public boolean removeFile(long id) throws SQLException {
        try (Connection c = db.open()) {
            return localFiles.delete(c, id);
        }
    }

    public List<HistoryEntry> listHistory() throws SQLException {
        try (Connection c = db.open()) {
            return history.findAll(c);
        }
    }

    public int clearHistory() throws SQLException {
        try (Connection c = db.open()) {
            return history.deleteAll(c);
        }
    }

    public String getSetting(String key, String defaultValue) throws SQLException {
        try (Connection c = db.open()) {
            return settings.get(c, key).orElse(defaultValue);
        }
    }

    public void saveSetting(String key, String value) throws SQLException {
        try (Connection c = db.open()) {
            settings.set(c, key, value);
        }
    }
}
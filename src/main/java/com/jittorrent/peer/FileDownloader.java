package com.jittorrent.peer;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;

import com.jittorrent.model.FileMetadata;
import com.jittorrent.model.PeerAddress;
import com.jittorrent.storage.HashMismatchException;
import com.jittorrent.tracker.TrackerClient;

public final class FileDownloader {
    private static final Logger LOG = Logger.getLogger(FileDownloader.class.getName());

    @FunctionalInterface
    private interface DbAction {
        void run() throws SQLException;
    }

    private FileDownloader() { }

    public static Path downloadFromTracker(TrackerClient tracker, String fileHash, Path downloadDir,
                                           DownloadTask.ProgressListener listener) throws IOException {
        return download(tracker, tracker.getMetadata(fileHash), downloadDir, listener);
    }

    /** Same download, plus a local_files row and a history row. */
    public static Path downloadAndRecord(TrackerClient tracker, PeerDataService data, String fileHash,
                                         Path downloadDir, DownloadTask.ProgressListener listener) throws IOException {
        FileMetadata meta = tracker.getMetadata(fileHash);
        Path target = DownloadTask.targetFor(downloadDir, meta);
        LocalDateTime started = LocalDateTime.now();

        record(() -> data.beginDownload(meta, target));
        try {
            Path saved = download(tracker, meta, downloadDir, listener);
            record(() -> data.completeDownload(meta, saved, started));
            return saved;
        } catch (IOException | RuntimeException e) {
            record(() -> data.failDownload(meta, target, started, e.getMessage()));
            throw e;
        }
    }

    private static Path download(TrackerClient tracker, FileMetadata meta, Path downloadDir,
                                 DownloadTask.ProgressListener listener) throws IOException {
        List<PeerAddress> seeders = tracker.getSeeders(meta.getFileHash());
        if (seeders.isEmpty()) {
            throw new IOException("No seeders are registered for this file");
        }

        Exception lastFailure = null;
        for (PeerAddress seeder : seeders) {
            try {
                return new DownloadTask(seeder.host(), seeder.port(), meta, downloadDir, listener).download();
            } catch (IOException | HashMismatchException e) {
                LOG.warning("Seeder " + seeder.host() + ":" + seeder.port() + " failed: " + e.getMessage());
                lastFailure = e;
            }
        }
        throw new IOException("All seeders failed", lastFailure);
    }

    private static void record(DbAction action) {
        try {
            action.run();
        } catch (SQLException e) {
            LOG.warning("Could not update the local database: " + e.getMessage());
        }
    }
}
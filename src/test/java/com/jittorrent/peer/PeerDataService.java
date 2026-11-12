package com.jittorrent.peer;

import com.jittorrent.db.DatabaseConnection;
import com.jittorrent.model.FileMetadata;
import com.jittorrent.model.HistoryEntry;
import com.jittorrent.model.LocalFile;
import com.jittorrent.model.LocalFileStatus;
import com.jittorrent.storage.ChunkingEngine;
import com.jittorrent.tracker.TrackerClient;
import com.jittorrent.tracker.TrackerServer;
import com.jittorrent.tracker.TrackerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class PeerDataServiceTest {

    private PeerDataService newService(Path dir) throws Exception {
        DatabaseConnection db = DatabaseConnection.forPeer(dir.resolve("peer.db").toString());
        db.initSchema();
        return new PeerDataService(db);
    }

    private FileMetadata sampleMeta() {
        return new FileMetadata("movie.bin", 1000L, "hash-1", 512, List.of("p0", "p1"));
    }

    @Test
    void recordingASeedTwiceKeepsOneRow(@TempDir Path dir) throws Exception {
        PeerDataService data = newService(dir);
        Path file = dir.resolve("movie.bin");
        data.recordSeed(sampleMeta(), file);
        data.recordSeed(sampleMeta(), file);

        List<LocalFile> files = data.listFiles();
        assertEquals(1, files.size());
        assertEquals(LocalFileStatus.SEEDING, files.get(0).status());
    }

    @Test
    void completedDownloadUpdatesStatusAndWritesHistory(@TempDir Path dir) throws Exception {
        PeerDataService data = newService(dir);
        Path target = dir.resolve("downloads/movie.bin");

        data.beginDownload(sampleMeta(), target);
        assertEquals(LocalFileStatus.DOWNLOADING, data.listFiles().get(0).status());

        data.completeDownload(sampleMeta(), target, LocalDateTime.now().minusSeconds(5));

        assertEquals(1, data.listFiles().size());
        assertEquals(LocalFileStatus.COMPLETE, data.listFiles().get(0).status());
        List<HistoryEntry> history = data.listHistory();
        assertEquals(1, history.size());
        assertEquals("SUCCESS", history.get(0).result());
        assertEquals("movie.bin", history.get(0).fileName());
    }

    @Test
    void failedDownloadIsRecordedWithItsReason(@TempDir Path dir) throws Exception {
        PeerDataService data = newService(dir);
        Path target = dir.resolve("movie.bin");

        data.beginDownload(sampleMeta(), target);
        data.failDownload(sampleMeta(), target, LocalDateTime.now(), "connection refused");

        assertEquals(LocalFileStatus.FAILED, data.listFiles().get(0).status());
        HistoryEntry entry = data.listHistory().get(0);
        assertEquals("FAILED", entry.result());
        assertEquals("connection refused", entry.detail());
    }

    @Test
    void completionWorksEvenIfBeginWasNeverRecorded(@TempDir Path dir) throws Exception {
        PeerDataService data = newService(dir);
        data.completeDownload(sampleMeta(), dir.resolve("movie.bin"), LocalDateTime.now());

        assertEquals(LocalFileStatus.COMPLETE, data.listFiles().get(0).status());
        assertEquals(1, data.listHistory().size());
    }

    @Test
    void dataSurvivesARestart(@TempDir Path dir) throws Exception {
        newService(dir).completeDownload(sampleMeta(), dir.resolve("movie.bin"), LocalDateTime.now());

        PeerDataService reopened = newService(dir); // a new object on the same database file
        assertEquals(1, reopened.listHistory().size());
        assertEquals(LocalFileStatus.COMPLETE, reopened.listFiles().get(0).status());
    }

    @Test
    void removeClearAndSettings(@TempDir Path dir) throws Exception {
        PeerDataService data = newService(dir);
        data.recordSeed(sampleMeta(), dir.resolve("a.bin"));
        data.completeDownload(sampleMeta(), dir.resolve("b.bin"), LocalDateTime.now());
        assertEquals(2, data.listFiles().size());

        long id = data.listFiles().get(0).id();
        assertTrue(data.removeFile(id));
        assertFalse(data.removeFile(id));
        assertEquals(1, data.listFiles().size());

        assertEquals(1, data.clearHistory());
        assertTrue(data.listHistory().isEmpty());

        assertEquals("downloads", data.getSetting("download.dir", "downloads"));
        data.saveSetting("download.dir", "D:/torrents");
        data.saveSetting("download.dir", "E:/torrents");
        assertEquals("E:/torrents", data.getSetting("download.dir", "downloads"));
    }

    @Test
    void trackerDownloadIsRecorded(@TempDir Path dir) throws Exception {
        Path source = dir.resolve("source.bin");
        byte[] content = new byte[300_000];
        new Random(3).nextBytes(content);
        Files.write(source, content);

        DatabaseConnection trackerDb = new DatabaseConnection(dir.resolve("tracker.db").toString());
        trackerDb.initSchema();
        TrackerServer tracker = new TrackerServer(0, new TrackerService(trackerDb));
        tracker.start();
        SeederServer seeder = new SeederServer(0);
        FileMetadata meta = seeder.share(source, 65536);
        seeder.start();
        try {
            TrackerClient client = new TrackerClient("localhost", tracker.getPort());
            client.registerFile(meta, seeder.getPort());
            PeerDataService data = newService(dir);

            Path saved = FileDownloader.downloadAndRecord(
                    client, data, meta.getFileHash(), dir.resolve("out"), null);

            assertEquals(-1, Files.mismatch(source, saved));
            assertEquals("SUCCESS", data.listHistory().get(0).result());
            assertEquals(LocalFileStatus.COMPLETE, data.listFiles().get(0).status());
        } finally {
            seeder.stop();
            tracker.stop();
        }
    }

    @Test
    void failedTrackerDownloadIsRecorded(@TempDir Path dir) throws Exception {
        Path source = dir.resolve("source.bin");
        Files.write(source, new byte[100_000]);
        FileMetadata meta = ChunkingEngine.createMetadata(source, 65536);

        DatabaseConnection trackerDb = new DatabaseConnection(dir.resolve("tracker.db").toString());
        trackerDb.initSchema();
        TrackerServer tracker = new TrackerServer(0, new TrackerService(trackerDb));
        tracker.start();
        try {
            int deadPort;
            try (ServerSocket tmp = new ServerSocket(0)) {
                deadPort = tmp.getLocalPort(); // nothing listens here once the socket closes
            }
            TrackerClient client = new TrackerClient("localhost", tracker.getPort());
            client.registerFile(meta, deadPort);
            PeerDataService data = newService(dir);

            assertThrows(IOException.class, () -> FileDownloader.downloadAndRecord(
                    client, data, meta.getFileHash(), dir.resolve("out"), null));

            assertEquals(LocalFileStatus.FAILED, data.listFiles().get(0).status());
            HistoryEntry entry = data.listHistory().get(0);
            assertEquals("FAILED", entry.result());
            assertTrue(entry.detail().contains("All seeders failed"));
        } finally {
            tracker.stop();
        }
    }
}
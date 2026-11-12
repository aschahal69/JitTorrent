package com.jittorrent.tracker;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.jittorrent.db.DatabaseConnection;
import com.jittorrent.model.FileMetadata;
import com.jittorrent.model.FileSummary;
import com.jittorrent.peer.FileDownloader;
import com.jittorrent.peer.SeederServer;

class TrackerFlowTest {

    private Path makeFile(Path dir) throws IOException {
        Path source = dir.resolve("source.bin");
        byte[] content = new byte[500_000];
        new Random(9).nextBytes(content);
        Files.write(source, content);
        return source;
    }

    @Test
    void registerListAndDownloadThroughTracker(@TempDir Path dir) throws Exception {
        Path source = makeFile(dir);
        DatabaseConnection db = new DatabaseConnection(dir.resolve("tracker.db").toString());
        db.initSchema();
        TrackerServer tracker = new TrackerServer(0, new TrackerService(db));
        tracker.start();
        SeederServer seeder = new SeederServer(0);
        FileMetadata meta = seeder.share(source, 65536);
        seeder.start();
        try {
            TrackerClient client = new TrackerClient("localhost", tracker.getPort());
            client.registerFile(meta, seeder.getPort());
            client.registerFile(meta, seeder.getPort()); // registering twice must not create duplicates

            List<FileSummary> files = client.listFiles();
            assertEquals(1, files.size());
            assertEquals(meta.getFileHash(), files.get(0).fileHash());
            assertEquals(1, client.getSeeders(meta.getFileHash()).size());
            assertEquals(meta.getPieceHash(3), client.getMetadata(meta.getFileHash()).getPieceHash(3));

            Path result = FileDownloader.downloadFromTracker(
                    client, meta.getFileHash(), dir.resolve("out"), null);
            assertEquals(-1, Files.mismatch(source, result));
        } finally {
            seeder.stop();
            tracker.stop();
        }
    }

    @Test
    void deadSeederIsSkipped(@TempDir Path dir) throws Exception {
        Path source = makeFile(dir);
        DatabaseConnection db = new DatabaseConnection(dir.resolve("tracker.db").toString());
        db.initSchema();
        TrackerServer tracker = new TrackerServer(0, new TrackerService(db));
        tracker.start();
        SeederServer seeder = new SeederServer(0);
        FileMetadata meta = seeder.share(source, 65536);
        seeder.start();
        try {
            int deadPort;
            try (ServerSocket tmp = new ServerSocket(0)) {
                deadPort = tmp.getLocalPort(); // nothing listens here once the socket closes
            }
            TrackerClient client = new TrackerClient("localhost", tracker.getPort());
            client.registerFile(meta, deadPort);          // registered first, so it is tried first
            client.registerFile(meta, seeder.getPort());

            Path result = FileDownloader.downloadFromTracker(
                    client, meta.getFileHash(), dir.resolve("out"), null);
            assertEquals(-1, Files.mismatch(source, result));
        } finally {
            seeder.stop();
            tracker.stop();
        }
    }

    @Test
    void unknownFileIsAnError(@TempDir Path dir) throws Exception {
        DatabaseConnection db = new DatabaseConnection(dir.resolve("tracker.db").toString());
        db.initSchema();
        TrackerServer tracker = new TrackerServer(0, new TrackerService(db));
        tracker.start();
        try {
            TrackerClient client = new TrackerClient("localhost", tracker.getPort());
            assertThrows(IOException.class, () -> client.getMetadata("nope"));
        } finally {
            tracker.stop();
        }
    }
}
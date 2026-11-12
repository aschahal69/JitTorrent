package com.jittorrent.peer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.jittorrent.model.FileMetadata;

class PeerTransferTest {

    @Test
    void downloadsFileFromSeeder(@TempDir Path dir) throws Exception {
        Path source = dir.resolve("source.bin");
        byte[] content = new byte[700_000];
        new Random(5).nextBytes(content);
        Files.write(source, content);

        SeederServer seeder = new SeederServer(0); // 0 = any free port
        FileMetadata meta = seeder.share(source, 65536);
        seeder.start();
        try {
            AtomicInteger lastProgress = new AtomicInteger();
            DownloadTask task = new DownloadTask("localhost", seeder.getPort(), meta.getFileHash(),
                    dir.resolve("out"), (done, total) -> lastProgress.set(done));

            Path result = task.download();

            assertEquals(-1, Files.mismatch(source, result));
            assertEquals(meta.getPieceCount(), lastProgress.get());
        } finally {
            seeder.stop();
        }
    }

    @Test
    void unknownFileIsReportedAsError(@TempDir Path dir) throws Exception {
        Path source = dir.resolve("source.bin");
        Files.write(source, new byte[1000]);

        SeederServer seeder = new SeederServer(0);
        seeder.share(source, 512);
        seeder.start();
        try {
            DownloadTask task = new DownloadTask("localhost", seeder.getPort(), "deadbeef",
                    dir.resolve("out"), null);
            IOException e = assertThrows(IOException.class, task::download);
            assertTrue(e.getMessage().contains("Unknown file"));
        } finally {
            seeder.stop();
        }
    }
}
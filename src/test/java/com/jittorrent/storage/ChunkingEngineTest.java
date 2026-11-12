package com.jittorrent.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.jittorrent.model.FileMetadata;

class ChunkingEngineTest {

    @Test
    void splitShuffleAndRebuild(@TempDir Path dir) throws Exception {
        Path source = dir.resolve("source.bin");
        byte[] content = new byte[1_000_123];
        new Random(42).nextBytes(content);
        Files.write(source, content);

        FileMetadata meta = ChunkingEngine.createMetadata(source, 65536);
        assertEquals(16, meta.getPieceCount());
        assertEquals(17083, meta.pieceLength(15)); // the shorter last piece

        ChunkingEngine seeder = new ChunkingEngine(meta, source);
        Path target = dir.resolve("copy.bin");
        ChunkingEngine downloader = new ChunkingEngine(meta, target);
        downloader.preallocate();

        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < meta.getPieceCount(); i++) order.add(i);
        Collections.shuffle(order, new Random(1));

        for (int index : order) {
            byte[] piece = seeder.readPiece(index);
            downloader.verifyPiece(index, piece);
            downloader.writePiece(index, piece);
        }

        assertTrue(downloader.verifyWholeFile());
        assertEquals(-1, Files.mismatch(source, target));
    }

    @Test
    void corruptedPieceIsRejected(@TempDir Path dir) throws Exception {
        Path source = dir.resolve("source.bin");
        byte[] content = new byte[200_000];
        new Random(7).nextBytes(content);
        Files.write(source, content);

        FileMetadata meta = ChunkingEngine.createMetadata(source, 65536);
        ChunkingEngine engine = new ChunkingEngine(meta, source);

        byte[] piece = engine.readPiece(0);
        piece[0] ^= 1; // flip one bit
        assertThrows(HashMismatchException.class, () -> engine.verifyPiece(0, piece));
    }
}
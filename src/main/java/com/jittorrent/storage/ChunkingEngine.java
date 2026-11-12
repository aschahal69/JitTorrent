package com.jittorrent.storage;

// byte array handling
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import com.jittorrent.model.FileMetadata;
import com.jittorrent.util.HashUtil;

public class ChunkingEngine {

    // private final because we are using the same instance of ChunkingEngine for the same file.
    private final FileMetadata meta;
    private final Path file;

    //1 file has 1 metada object
    public ChunkingEngine(FileMetadata meta, Path file) {
        this.meta = meta;
        this.file = file;
    }
    
    // Seeder side: split a file into pieces and build its metadata.
     public static FileMetadata createMetadata(Path file, int pieceSize) throws IOException {
        long size = Files.size(file);

        // why chunk a file which dosent exist.
        if (size == 0) {
            throw new IllegalArgumentException("Cannot share an empty file");
        }

        // create an object of messagedigest api with a new hashing engine.
        // create an empty vector to get hashes of pieces
        MessageDigest wholeFile = HashUtil.newSha1();
        List<String> pieceHashes = new ArrayList<>();

        // buffer to limit size of pieces to piecesize
        byte[] buffer = new byte[pieceSize];

        // read file
        try (InputStream in = Files.newInputStream(file)) {

            int n;

            // buffer is receiving
            // in wholefile object append the files pieces in chunks
            // in piecehash list add the calculated sha1 hash of the piece of file we just read
            // hash util is calles to operate on the byte array in the buffer
            while ((n = in.readNBytes(buffer, 0, pieceSize)) > 0) {
                wholeFile.update(buffer, 0, n);
                pieceHashes.add(HashUtil.sha1(buffer, n));
            }
        }
        return new FileMetadata(file.getFileName().toString(), size,
                HexFormat.of().formatHex(wholeFile.digest()), pieceSize, pieceHashes);
    }

    // Seeder side: read one piece from disk.

    // when we get the metadata of file we already have the files pieces and index.
    // when uploading we are uploading in pieces so we first read a piece from its index
    public byte[] readPiece(int index) throws IOException {
        checkIndex(index);

        // a byte array that stores the length of the piece at the input index
        byte[] data = new byte[meta.pieceLength(index)];

        // read file
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            raf.seek((long) index * meta.getPieceSize());
            raf.readFully(data);
        }
        return data;
    }

    // Leecher side: create the target file at full size so pieces can land anywhere.

    // only create the preallocated file once when doenloading a file
    // thes method that throws exception achieves this only 
    public void preallocate() throws IOException {
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw")) {
            raf.setLength(meta.getFileSize());
        }
    }

    // Leecher side: write one piece at its correct offset, in any order.
    public void writePiece(int index, byte[] data) throws IOException {
        checkIndex(index);
        if (data.length != meta.pieceLength(index)) {
            throw new IllegalArgumentException("Wrong length for piece " + index);
        }
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw")) {
            raf.seek((long) index * meta.getPieceSize());
            raf.write(data);
        }
    }

    public void verifyPiece(int index, byte[] data) throws HashMismatchException {
        checkIndex(index);
        boolean lengthOk = data.length == meta.pieceLength(index);
        // to check hash calculate hash again and compare with hash in hashlist of metadata file
        if (!lengthOk || !HashUtil.sha1(data).equals(meta.getPieceHash(index))) {
            throw new HashMismatchException(index);
        }
    }

    public boolean verifyWholeFile() throws IOException {
        return HashUtil.sha1OfFile(file).equals(meta.getFileHash());
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= meta.getPieceCount()) {
            throw new IllegalArgumentException("Piece index out of range: " + index);
        }
    }


}

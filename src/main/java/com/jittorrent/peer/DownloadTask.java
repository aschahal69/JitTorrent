package com.jittorrent.peer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Path;
import java.util.logging.Logger;

import com.jittorrent.model.FileMetadata;
import com.jittorrent.protocol.ErrorMessage;
import com.jittorrent.protocol.InvalidMessageException;
import com.jittorrent.protocol.Message;
import com.jittorrent.protocol.MetadataMessage;
import com.jittorrent.protocol.PieceDataMessage;
import com.jittorrent.protocol.RequestMetadataMessage;
import com.jittorrent.protocol.RequestPieceMessage;
import com.jittorrent.storage.ChunkingEngine;
import com.jittorrent.storage.HashMismatchException;

public class DownloadTask {
    private static final Logger LOG = Logger.getLogger(DownloadTask.class.getName());
    private static final int MAX_RETRIES = 3;
    private static final int TIMEOUT_MS = 10_000;

    /** The JavaFX progress bar will implement this later. */
    @FunctionalInterface
    public interface ProgressListener {
        void onProgress(int piecesDone, int totalPieces);
    }

    private final String host;
    private final int port;
    private final String fileHash;
    private final FileMetadata trustedMeta; // from the tracker; null means "ask the seeder"
    private final Path downloadDir;
    private final ProgressListener listener;

    /** Direct mode: the seeder supplies the metadata. */
    public DownloadTask(String host, int port, String fileHash, Path downloadDir, ProgressListener listener) {
        this(host, port, fileHash, null, downloadDir, listener);
    }

    /** Tracker mode: metadata and piece hashes come from the tracker, so the seeder only supplies pieces. */
    public DownloadTask(String host, int port, FileMetadata trustedMeta, Path downloadDir, ProgressListener listener) {
        this(host, port, trustedMeta.getFileHash(), trustedMeta, downloadDir, listener);
    }

    //the 2 constructors ultimately defer to this constructor
    public DownloadTask(String host, int port, String fileHash, FileMetadata trustedMeta,
                         Path downloadDir, ProgressListener listener) {
        this.host = host;
        this.port = port;
        this.fileHash = fileHash;
        this.trustedMeta = trustedMeta;
        this.downloadDir = downloadDir;
        this.listener = listener != null ? listener : (done, total) -> { };
    }

    public static Path targetFor(Path downloadDir, FileMetadata meta) {
        String safeName = Path.of(meta.getFileName()).getFileName().toString();
        return downloadDir.resolve(safeName);
    }

    /** Downloads the whole file and returns where it was saved. */
    public Path download() throws IOException, HashMismatchException {

        //TCP handshake with already fed host ip and port
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), TIMEOUT_MS);
            socket.setSoTimeout(TIMEOUT_MS);
            DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            
            // metadata object is initialized by receiving byte stream
            FileMetadata meta = trustedMeta != null ? trustedMeta : fetchMetadata(in, out);

            // getting filename from metadata and putting it into a string
            String safeName = Path.of(meta.getFileName()).getFileName().toString();
            Path target = targetFor(downloadDir, meta);
            // Chunking process started with metadata object and final pathname as input
            ChunkingEngine engine = new ChunkingEngine(meta, target);
            // preallocate method from chunking engine class
            engine.preallocate();

            LOG.info("Downloading " + safeName + " (" + meta.getPieceCount() + " pieces)");
            for (int i = 0; i < meta.getPieceCount(); i++) {
                fetchPiece(in, out, engine, i);
                listener.onProgress(i + 1, meta.getPieceCount());
            }

            if (!engine.verifyWholeFile()) {
                throw new IOException("Whole-file hash check failed");
            }
            LOG.info("Download complete: " + target);
            return target;
        }
    }

    private FileMetadata fetchMetadata(DataInputStream in, DataOutputStream out) throws IOException {
        new RequestMetadataMessage(fileHash).write(out);
        Message reply = Message.read(in);
        if (reply instanceof ErrorMessage e) {
            throw new IOException("Seeder error: " + e.getText());
        }
        if (!(reply instanceof MetadataMessage m)) {
            throw new InvalidMessageException("Expected metadata but got " + reply.type());
        }
        if (!m.getMetadata().getFileHash().equals(fileHash)) {
            throw new IOException("Seeder sent metadata for a different file");
        }
        return m.getMetadata();
    }

    private void fetchPiece(DataInputStream in, DataOutputStream out,
                            ChunkingEngine engine, int index) throws IOException, HashMismatchException {
        HashMismatchException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            new RequestPieceMessage(fileHash, index).write(out);
            Message reply = Message.read(in);
            if (reply instanceof ErrorMessage e) {
                throw new IOException("Seeder error: " + e.getText());
            }
            if (!(reply instanceof PieceDataMessage piece)) {
                throw new InvalidMessageException("Expected piece data but got " + reply.type());
            }
            if (piece.getPieceIndex() != index) {
                throw new InvalidMessageException("Received piece " + piece.getPieceIndex() + ", expected " + index);
            }
            try {
                engine.verifyPiece(index, piece.getData());
                engine.writePiece(index, piece.getData());
                return;
            } catch (HashMismatchException e) {
                LOG.warning("Piece " + index + " corrupted (attempt " + attempt + "), retrying");
                lastFailure = e;
            }
        }
        throw lastFailure;
    }
}
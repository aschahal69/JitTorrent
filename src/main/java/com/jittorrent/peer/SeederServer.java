package com.jittorrent.peer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import com.jittorrent.model.FileMetadata;
import com.jittorrent.protocol.ErrorMessage;
import com.jittorrent.protocol.Message;
import com.jittorrent.protocol.MetadataMessage;
import com.jittorrent.protocol.PieceDataMessage;
import com.jittorrent.protocol.RequestMetadataMessage;
import com.jittorrent.protocol.RequestPieceMessage;
import com.jittorrent.storage.ChunkingEngine;

public class SeederServer {

    //log when a peer wants to seed
    private static final Logger LOG = Logger.getLogger(SeederServer.class.getName());

    // not required but advised to have timeout
    private static final int IDLE_TIMEOUT_MS = 30_000;

    // the file that is to be shared is identified by its metadata
    private record SharedFile(FileMetadata meta, ChunkingEngine engine) { }

    // a port is constant for sharing 1 file
    private final int port;

    // concurrency is maintained by using file to peer address map
    private final Map<String, SharedFile> files = new ConcurrentHashMap<>();
    private final ExecutorService pool = Executors.newCachedThreadPool();

    // the heavylifting is done by java.net library
    // the server socket  waits for and accepts incoming TCP network connections. no need to implement TCP from scratch
    private ServerSocket serverSocket;
    private volatile boolean running;

    public SeederServer(int port) {
        this.port = port;
    }

    /** Make a file available to other peers. Returns its metadata (including the file hash). */
    public FileMetadata share(Path file, int pieceSize) throws IOException {

        //create metadat when peer wants to share a file
        FileMetadata meta = ChunkingEngine.createMetadata(file, pieceSize);

        
        files.put(meta.getFileHash(), new SharedFile(meta, new ChunkingEngine(meta, file)));
        LOG.info("Sharing " + meta.getFileName() + " (" + meta.getPieceCount() + " pieces)");
        return meta;
    }

    public void start() throws IOException {

        // start listening at port for peers to send accept()
        serverSocket = new ServerSocket(port);
        running = true;

        //there must be 1 thread per connection
        //A seeder must serve several peers at once. accept() blocks outgoing tcp broadcast so each accepted socket is handed to an ExecutorService and one slow peer can’t block the others.
        Thread acceptor = new Thread(this::acceptLoop, "seeder-acceptor");
        acceptor.start();
        LOG.info("Seeder listening on port " + getPort());
    }

    /** Useful when the server was created with port 0 (the OS picks a free port). */
    public int getPort() {
        return serverSocket.getLocalPort();
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException e) {
            LOG.warning("Error closing server socket: " + e.getMessage());
        }
        pool.shutdownNow();
        LOG.info("Seeder stopped");
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                pool.submit(() -> handle(socket));
            } catch (IOException e) {
                if (running) LOG.warning("Accept failed: " + e.getMessage());
            }
        }
    }

    /** One of these runs per connected peer, on its own thread. */
    private void handle(Socket socket) {

        // get ip address of peer
        String peer = String.valueOf(socket.getRemoteSocketAddress());
        LOG.info("Peer connected: " + peer);
        try (socket) {
            socket.setSoTimeout(IDLE_TIMEOUT_MS);
            DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            while (true) {
                Message request = Message.read(in);
                respond(request).write(out);
            }
        } catch (EOFException e) {
            LOG.info("Peer disconnected: " + peer);
        } catch (IOException e) {
            LOG.warning("Connection with " + peer + " ended: " + e.getMessage());
        }
    }

    private Message respond(Message request) throws IOException {
        if (request instanceof RequestMetadataMessage r) {
            SharedFile f = files.get(r.getFileHash());
            return f == null ? new ErrorMessage("Unknown file") : new MetadataMessage(f.meta());
        }
        if (request instanceof RequestPieceMessage r) {
            SharedFile f = files.get(r.getFileHash());
            if (f == null) return new ErrorMessage("Unknown file");
            int index = r.getPieceIndex();
            if (index < 0 || index >= f.meta().getPieceCount()) {
                return new ErrorMessage("Piece index out of range");
            }
            return new PieceDataMessage(r.getFileHash(), index, f.engine().readPiece(index));
        }
        return new ErrorMessage("Unexpected message type: " + request.type());
    }
}
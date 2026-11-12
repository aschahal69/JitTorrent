package com.jittorrent.tracker;

import com.jittorrent.model.FileMetadata;
import com.jittorrent.protocol.*;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TrackerServer {
    private static final Logger LOG = Logger.getLogger(TrackerServer.class.getName());
    private static final int IDLE_TIMEOUT_MS = 30_000;

    private final int port;
    private final TrackerService service;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private ServerSocket serverSocket;
    private volatile boolean running;

    public TrackerServer(int port, TrackerService service) {
        this.port = port;
        this.service = service;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        new Thread(this::acceptLoop, "tracker-acceptor").start();
        LOG.info("Tracker listening on port " + getPort());
    }

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
        LOG.info("Tracker stopped");
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

    private void handle(Socket socket) {
        String peerIp = socket.getInetAddress().getHostAddress();
        try (socket) {
            socket.setSoTimeout(IDLE_TIMEOUT_MS);
            DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            while (true) {
                Message request = Message.read(in);
                respond(request, peerIp).write(out);
            }
        } catch (EOFException e) {
            // client closed the connection normally
        } catch (IOException e) {
            LOG.warning("Connection with " + peerIp + " ended: " + e.getMessage());
        }
    }

    private Message respond(Message request, String peerIp) {
        try {
            if (request instanceof RegisterFileMessage r) {
                int peerPort = r.getPeerPort();
                if (peerPort < 1 || peerPort > 65535) {
                    return new ErrorMessage("Invalid port");
                }
                // The IP comes from the connection itself, not from anything the peer claims.
                service.registerFile(r.getMetadata(), peerIp, peerPort);
                LOG.info("Registered " + r.getMetadata().getFileName() + " seeded by " + peerIp + ":" + peerPort);
                return new OkMessage();
            }
            if (request instanceof ListFilesMessage) {
                return new FileListMessage(service.listFiles());
            }
            if (request instanceof RequestMetadataMessage r) {
                Optional<FileMetadata> meta = service.getMetadata(r.getFileHash());
                return meta.isPresent() ? new MetadataMessage(meta.get()) : new ErrorMessage("Unknown file");
            }
            if (request instanceof GetSeedersMessage r) {
                return new SeederListMessage(service.getSeeders(r.getFileHash()));
            }
            return new ErrorMessage("Unexpected message type: " + request.type());
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Database error", e);
            return new ErrorMessage("Tracker database error");
        }
    }
}
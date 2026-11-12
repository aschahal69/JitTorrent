package com.jittorrent.peer;

import java.io.IOException;
import java.nio.file.Path;

import com.jittorrent.model.FileMetadata;
import com.jittorrent.tracker.TrackerClient;
import com.jittorrent.util.ConfigLoader;

public class SeederMain {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: SeederMain <file> [port]");
            return;
        }
        int port = args.length > 1 ? Integer.parseInt(args[1]) : ConfigLoader.getInt("peer.port");

        SeederServer server = new SeederServer(port);
        FileMetadata meta = server.share(Path.of(args[0]), ConfigLoader.getInt("piece.size"));
        server.start();

        System.out.println("Seeding " + meta.getFileName() + " on port " + server.getPort());
        System.out.println("File hash: " + meta.getFileHash());

        try {
            new TrackerClient(ConfigLoader.get("tracker.host"), ConfigLoader.getInt("tracker.port"))
                    .registerFile(meta, server.getPort());
            System.out.println("Registered with the tracker.");
        } catch (IOException e) {
            System.out.println("Could not register with the tracker: " + e.getMessage());
        }

        System.out.println("Press Enter to stop.");
        System.in.read();
        server.stop();
    }
}
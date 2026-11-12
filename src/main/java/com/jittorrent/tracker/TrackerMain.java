package com.jittorrent.tracker;

import com.jittorrent.db.DatabaseConnection;
import com.jittorrent.util.ConfigLoader;

public class TrackerMain {
    public static void main(String[] args) throws Exception {
        DatabaseConnection db = new DatabaseConnection(ConfigLoader.get("tracker.db.path"));
        db.initSchema();

        TrackerServer server = new TrackerServer(ConfigLoader.getInt("tracker.port"), new TrackerService(db));
        server.start();

        System.out.println("Tracker running on port " + server.getPort());
        System.out.println("Press Enter to stop.");
        System.in.read();
        server.stop();
    }
}
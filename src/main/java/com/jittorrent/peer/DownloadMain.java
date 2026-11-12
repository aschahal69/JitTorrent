package com.jittorrent.peer;

import java.nio.file.Path;

import com.jittorrent.db.DatabaseConnection;
import com.jittorrent.model.FileSummary;
import com.jittorrent.model.HistoryEntry;
import com.jittorrent.model.LocalFile;
import com.jittorrent.tracker.TrackerClient;
import com.jittorrent.util.ConfigLoader;

public class DownloadMain {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: DownloadMain list      (files on the tracker)");
            System.out.println("       DownloadMain files     (files on this machine)");
            System.out.println("       DownloadMain history   (download history)");
            System.out.println("       DownloadMain <fileHash> [downloadDir]");
            return;
        }
        DatabaseConnection db = DatabaseConnection.forPeer(ConfigLoader.get("peer.db.path"));
        db.initSchema();
        PeerDataService data = new PeerDataService(db);
        TrackerClient tracker = new TrackerClient(
                ConfigLoader.get("tracker.host"), ConfigLoader.getInt("tracker.port"));

        switch (args[0]) {
            case "list" -> {
                for (FileSummary f : tracker.listFiles()) {
                    System.out.printf("%s  (%d bytes, %d pieces)%n  hash: %s%n",
                            f.fileName(), f.fileSize(), f.pieceCount(), f.fileHash());
                }
            }
            case "files" -> {
                for (LocalFile f : data.listFiles()) {
                    System.out.printf("[%s] %s%n  %s%n", f.status(), f.fileName(), f.savePath());
                }
            }
            case "history" -> {
                for (HistoryEntry h : data.listHistory()) {
                    System.out.printf("%s  %-8s %s %s%n", h.finishedAt(), h.result(), h.fileName(),
                            h.detail().isEmpty() ? "" : "(" + h.detail() + ")");
                }
            }
            default -> {
                Path dir = Path.of(args.length > 1 ? args[1] : ConfigLoader.get("download.dir"));
                Path saved = FileDownloader.downloadAndRecord(tracker, data, args[0], dir,
                        (done, total) -> System.out.printf("\r%d / %d pieces (%d%%)", done, total, done * 100 / total));
                System.out.println("\nSaved to " + saved);
            }
        }
    }
}
package com.jittorrent.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Logger;

import com.jittorrent.db.DatabaseConnection;
import com.jittorrent.model.FileMetadata;
import com.jittorrent.model.LocalFile;
import com.jittorrent.model.LocalFileStatus;
import com.jittorrent.peer.PeerDataService;
import com.jittorrent.peer.SeederServer;
import com.jittorrent.tracker.TrackerClient;
import com.jittorrent.util.ConfigLoader;

import javafx.application.Platform;
import javafx.concurrent.Task;

/** Everything the screens share: services, settings, the seeder, and the background worker pool. */
public class AppContext {
    private static final Logger LOG = Logger.getLogger(AppContext.class.getName());

    public static final String TRACKER_HOST = "tracker.host";
    public static final String TRACKER_PORT = "tracker.port";
    public static final String PEER_PORT = "peer.port";
    public static final String DOWNLOAD_DIR = "download.dir";
    private static final List<String> SETTING_KEYS = List.of(TRACKER_HOST, TRACKER_PORT, PEER_PORT, DOWNLOAD_DIR);

    /** trackerWarning is null when the tracker was told about the file, otherwise the reason it was not. */
    public record SeedResult(FileMetadata meta, String trackerWarning) { }

    private final PeerDataService data;
    private final Map<String, String> settings = new ConcurrentHashMap<>();
    private final List<Runnable> dataListeners = new CopyOnWriteArrayList<>();
    private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "worker");
        thread.setDaemon(true); // workers must never keep the JVM alive after the window closes
        return thread;
    });
    private SeederServer seeder; // created on first use, guarded by 'this'

    public AppContext() throws SQLException {
        DatabaseConnection db = DatabaseConnection.forPeer(ConfigLoader.get("peer.db.path"));
        db.initSchema();
        this.data = new PeerDataService(db);
        for (String key : SETTING_KEYS) {
            settings.put(key, data.getSetting(key, ConfigLoader.get(key)));
        }
    }

    public PeerDataService data() {
        return data;
    }

    // ---- settings (cached, so reading one never touches the disk) ----

    public String setting(String key) {
        return settings.get(key);
    }

    public int intSetting(String key) {
        try {
            return Integer.parseInt(setting(key).trim());
        } catch (NumberFormatException e) {
            return ConfigLoader.getInt(key);
        }
    }

    public void saveSettings(Map<String, String> values) throws SQLException {
        for (Map.Entry<String, String> entry : values.entrySet()) {
            data.saveSetting(entry.getKey(), entry.getValue());
            settings.put(entry.getKey(), entry.getValue());
        }
    }

    public TrackerClient tracker() {
        return new TrackerClient(setting(TRACKER_HOST), intSetting(TRACKER_PORT));
    }

    public String trackerAddress() {
        return setting(TRACKER_HOST) + ":" + intSetting(TRACKER_PORT);
    }

    public Path downloadDir() {
        return Path.of(setting(DOWNLOAD_DIR));
    }

    // ---- seeding ----

    private synchronized SeederServer seeder() throws IOException {
        if (seeder == null) {
            SeederServer server = new SeederServer(intSetting(PEER_PORT));
            server.start();
            seeder = server;
        }
        return seeder;
    }

    /** Share a file, remember it locally, and tell the tracker. Hashing can be slow: call from a worker thread. */
    public SeedResult seedFile(Path file) throws IOException, SQLException {
        SeederServer server = seeder();
        FileMetadata meta = server.share(file, ConfigLoader.getInt("piece.size"));
        data.recordSeed(meta, file);
        String warning = null;
        try {
            tracker().registerFile(meta, server.getPort());
        } catch (IOException e) {
            warning = Dialogs.messageOf(e);
        }
        return new SeedResult(meta, warning);
    }

    /** After a restart, files recorded as SEEDING are shared again, if they are still on disk. */
    public void resumeSeeding() {
        executor.execute(() -> {
            try {
                for (LocalFile file : data.listFiles()) {
                    if (file.status() != LocalFileStatus.SEEDING) continue;
                    try {
                        Path path = Path.of(file.savePath());
                        if (Files.isRegularFile(path)) {
                            seedFile(path);
                        }
                    } catch (IOException | SQLException | RuntimeException e) {
                        LOG.warning("Could not resume seeding " + file.savePath() + ": " + e.getMessage());
                    }
                }
            } catch (SQLException e) {
                LOG.warning("Could not read the local database: " + e.getMessage());
            }
            fireDataChanged();
        });
    }

    // ---- background work and change notification ----

    public void execute(Runnable task) {
        executor.execute(task);
    }

    /** Run 'work' on a worker thread; the two callbacks run back on the JavaFX thread. */
    public <T> void runAsync(Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(event -> onSuccess.accept(task.getValue()));
        task.setOnFailed(event -> onError.accept(task.getException()));
        executor.execute(task);
    }

    public void addDataListener(Runnable listener) {
        dataListeners.add(listener);
    }

    /** Tells every screen that the local data changed so they reload. Safe to call from any thread. */
    public void fireDataChanged() {
        Platform.runLater(() -> dataListeners.forEach(Runnable::run));
    }

    public void shutdown() {
        SeederServer server;
        synchronized (this) {
            server = seeder;
            seeder = null;
        }
        if (server != null) server.stop();
        executor.shutdownNow();
    }
}
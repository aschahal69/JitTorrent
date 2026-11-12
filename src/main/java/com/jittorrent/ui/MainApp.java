package com.jittorrent.ui;

import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;

public class MainApp extends Application {
    private static final Logger LOG = Logger.getLogger(MainApp.class.getName());

    private AppContext ctx;
    private Exception initError;

    /** Runs before the window exists: open the database and load the settings. */
    @Override
    public void init() {
        try {
            ctx = new AppContext();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Startup failed", e);
            initError = e;
        }
    }

    @Override
    public void start(Stage stage) {
        if (initError != null) {
            Dialogs.error("MiniTorrent could not start", Dialogs.messageOf(initError));
            Platform.exit();
            return;
        }

        DownloadsTab downloads = new DownloadsTab(ctx);
        AvailableFilesTab available = new AvailableFilesTab(ctx, downloads);
        TabPane tabs = new TabPane(available, downloads, new MyFilesTab(ctx), new HistoryTab(ctx),
                new SettingsTab(ctx));

        stage.setTitle("MiniTorrent");
        stage.setScene(new Scene(tabs, 900, 560));
        stage.show();

        ctx.resumeSeeding();
        available.refresh(false);
    }

    /** Runs when the window closes: stop the seeder and the worker threads. */
    @Override
    public void stop() {
        if (ctx != null) {
            ctx.shutdown();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
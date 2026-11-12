package com.jittorrent.ui;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Map;

import com.jittorrent.tracker.TrackerClient;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.DirectoryChooser;

public class SettingsTab extends Tab {
    private record Values(String host, int trackerPort, String downloadDir, int seedPort) { }

    private final AppContext ctx;
    private final TextField trackerHost = new TextField(); 
    private final TextField trackerPort = new TextField();
    private final TextField downloadDir = new TextField();
    private final TextField peerPort = new TextField();
    private final Label status = new Label();

    public SettingsTab(AppContext ctx) {
        super("Settings");
        this.ctx = ctx;
        setClosable(false);

        trackerHost.setText(ctx.setting(AppContext.TRACKER_HOST));
        trackerPort.setText(ctx.setting(AppContext.TRACKER_PORT));
        downloadDir.setText(ctx.setting(AppContext.DOWNLOAD_DIR));
        peerPort.setText(ctx.setting(AppContext.PEER_PORT));
        trackerHost.setPrefColumnCount(26);
        downloadDir.setPrefColumnCount(26);

        Button browseButton = new Button("Browse...");
        browseButton.setOnAction(event -> chooseFolder());
        Button saveButton = new Button("Save");
        saveButton.setOnAction(event -> save());
        Button testButton = new Button("Test tracker connection");
        testButton.setOnAction(event -> testConnection());

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.setPadding(new Insets(20));
        grid.addRow(0, new Label("Tracker address"), trackerHost);
        grid.addRow(1, new Label("Tracker port"), trackerPort);
        grid.addRow(2, new Label("Download folder"), downloadDir, browseButton);
        grid.addRow(3, new Label("My seeding port"), peerPort);
        grid.add(new HBox(8, saveButton, testButton), 1, 4);
        grid.add(status, 1, 5);
        setContent(grid);
    }

    private void chooseFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose the download folder");
        File folder = chooser.showDialog(getTabPane().getScene().getWindow());
        if (folder != null) {
            downloadDir.setText(folder.getAbsolutePath());
        }
    }

    private void save() {
        Values values = readValues();
        if (values == null) return;
        Map<String, String> toSave = Map.of(
                AppContext.TRACKER_HOST, values.host(),
                AppContext.TRACKER_PORT, String.valueOf(values.trackerPort()),
                AppContext.DOWNLOAD_DIR, values.downloadDir(),
                AppContext.PEER_PORT, String.valueOf(values.seedPort()));
        ctx.runAsync(
                () -> {
                    ctx.saveSettings(toSave);
                    return null;
                },
                done -> status.setText("Saved. The tracker and download folder apply now; "
                        + "the seeding port applies the next time the app starts."),
                error -> Dialogs.error("Could not save the settings", Dialogs.messageOf(error)));
    }

    /** Tests the values currently typed in the form, so you can check them before saving. */
    private void testConnection() {
        Values values = readValues();
        if (values == null) return;
        status.setText("Testing the connection...");
        ctx.runAsync(
                () -> new TrackerClient(values.host(), values.trackerPort()).listFiles().size(),
                count -> status.setText("Connected. The tracker lists " + count + " file(s)."),
                error -> {
                    status.setText("Could not connect.");
                    Dialogs.error("Could not reach the tracker", Dialogs.messageOf(error));
                });
    }

    /** Validates the form. On a problem it shows a dialog and returns null. */
    private Values readValues() {
        try {
            String host = trackerHost.getText().trim();
            String dir = downloadDir.getText().trim();
            if (host.isEmpty()) {
                throw new IllegalArgumentException(
                        "Enter the tracker's address: an IP such as 192.168.1.20, or localhost.");
            }
            if (dir.isEmpty()) {
                throw new IllegalArgumentException("Choose a download folder.");
            }
            try {
                Path.of(dir);
            } catch (InvalidPathException e) {
                throw new IllegalArgumentException("The download folder is not a valid path.");
            }
            return new Values(host, parsePort(trackerPort.getText(), "Tracker port"), dir,
                    parsePort(peerPort.getText(), "Seeding port"));
        } catch (IllegalArgumentException e) {
            Dialogs.error("Invalid settings", e.getMessage());
            return null;
        }
    }

    private static int parsePort(String text, String label) {
        try {
            int port = Integer.parseInt(text.trim());
            if (port < 1 || port > 65535) throw new NumberFormatException();
            return port;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + " must be a number from 1 to 65535.");
        }
    }
}
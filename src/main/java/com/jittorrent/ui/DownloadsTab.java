package com.jittorrent.ui;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

import com.jittorrent.model.FileSummary;
import com.jittorrent.peer.FileDownloader;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.ProgressBarTableCell;

public class DownloadsTab extends Tab {

    /** One row in the table. The properties let the table update itself when they change. */
    private static final class DownloadItem {
        private final String fileHash;
        private final StringProperty name;
        private final DoubleProperty progress = new SimpleDoubleProperty(-1); // -1 shows an indeterminate bar
        private final StringProperty status = new SimpleStringProperty("Starting...");
        private boolean active = true; // only touched on the JavaFX thread

        DownloadItem(String fileHash, String name) {
            this.fileHash = fileHash;
            this.name = new SimpleStringProperty(name);
        }
    }

    private final AppContext ctx;
    private final ObservableList<DownloadItem> items = FXCollections.observableArrayList();

    public DownloadsTab(AppContext ctx) {
        super("Downloads");
        this.ctx = ctx;
        setClosable(false);

        TableColumn<DownloadItem, String> nameColumn = new TableColumn<>("File");
        nameColumn.setCellValueFactory(cell -> cell.getValue().name);
        nameColumn.setPrefWidth(260);

        TableColumn<DownloadItem, Double> progressColumn = new TableColumn<>("Progress");
        progressColumn.setCellValueFactory(cell -> cell.getValue().progress.asObject());
        progressColumn.setCellFactory(ProgressBarTableCell.forTableColumn());
        progressColumn.setPrefWidth(200);

        TableColumn<DownloadItem, String> statusColumn = new TableColumn<>("Status");
        statusColumn.setCellValueFactory(cell -> cell.getValue().status);
        statusColumn.setPrefWidth(380);

        TableView<DownloadItem> table = new TableView<>(items);
        table.getColumns().add(nameColumn);
        table.getColumns().add(progressColumn);
        table.getColumns().add(statusColumn);
        table.setPlaceholder(new Label("Downloads you start will appear here."));
        setContent(table);
    }

    /** Returns true if the Downloads tab should be shown (a download started or is already running). */
    public boolean start(FileSummary file) {
        for (DownloadItem item : items) {
            if (item.active && item.fileHash.equals(file.fileHash())) {
                Dialogs.info("Already downloading", file.fileName() + " is already being downloaded.");
                return true;
            }
        }

        // Downloading writes straight into the download folder, so warn before replacing a file.
        try {
            Path target = ctx.downloadDir().resolve(Path.of(file.fileName()).getFileName().toString());
            if (Files.exists(target) && !Dialogs.confirm("File already exists",
                    target + " already exists and will be overwritten. Continue?")) {
                return false;
            }
        } catch (InvalidPathException e) {
            Dialogs.error("Cannot save this file",
                    "The file name or the download folder is not a valid path on this computer.");
            return false;
        }

        DownloadItem item = new DownloadItem(file.fileHash(), file.fileName());
        items.add(0, item);

        Task<Path> task = new Task<>() {
            @Override
            protected Path call() throws Exception {
                return FileDownloader.downloadAndRecord(ctx.tracker(), ctx.data(), file.fileHash(),
                        ctx.downloadDir(), (done, total) -> updateProgress(done, total));
            }
        };
        item.progress.bind(task.progressProperty());
        item.status.set("Downloading...");

        task.setOnSucceeded(event -> finish(item, "Complete: " + task.getValue(), 1.0));
        task.setOnFailed(event -> {
            String message = Dialogs.messageOf(task.getException());
            finish(item, "Failed: " + message, Math.max(item.progress.get(), 0.0));
            Dialogs.error("Download failed", file.fileName() + "\n\n" + message);
        });
        ctx.execute(task);
        return true;
    }

    private void finish(DownloadItem item, String statusText, double finalProgress) {
        item.progress.unbind(); // a bound property cannot be set
        item.progress.set(finalProgress);
        item.status.set(statusText);
        item.active = false;
        ctx.fireDataChanged(); // My Files and History reload
    }
}
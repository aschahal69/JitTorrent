package com.jittorrent.ui;

import com.jittorrent.model.FileSummary;
import com.jittorrent.util.SizeFormat;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;

import java.util.List;

public class AvailableFilesTab extends Tab {
    private record Catalog(String tracker, List<FileSummary> files) { }

    private final AppContext ctx;
    private final DownloadsTab downloads;
    private final TableView<FileSummary> table = new TableView<>();
    private final Label status = new Label("Not loaded yet");

    public AvailableFilesTab(AppContext ctx, DownloadsTab downloads) {
        super("Available Files");
        this.ctx = ctx;
        this.downloads = downloads;
        setClosable(false);

        table.getColumns().add(Tables.textColumn("File", 320, FileSummary::fileName));
        table.getColumns().add(Tables.textColumn("Size", 100, f -> SizeFormat.humanReadable(f.fileSize())));
        table.getColumns().add(Tables.textColumn("Pieces", 80, f -> String.valueOf(f.pieceCount())));
        table.getColumns().add(Tables.textColumn("Hash", 200, f -> f.fileHash().substring(0, 12) + "..."));
        table.setPlaceholder(new Label("No files to show. Press Refresh, or ask someone to seed a file."));

        Button refreshButton = new Button("Refresh");
        refreshButton.setOnAction(event -> refresh(true));
        Button downloadButton = new Button("Download selected");
        downloadButton.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        downloadButton.setOnAction(event -> downloadSelected());

        HBox bar = new HBox(8, refreshButton, downloadButton);
        bar.setPadding(new Insets(10));
        status.setPadding(new Insets(6, 10, 6, 10));

        BorderPane root = new BorderPane(table);
        root.setTop(bar);
        root.setBottom(status);
        setContent(root);
    }

    /** showAlert is false for the automatic load at startup, so a missing tracker doesn't pop up a dialog. */
    public void refresh(boolean showAlert) {
        status.setText("Contacting the tracker...");
        ctx.runAsync(
                () -> new Catalog(ctx.trackerAddress(), ctx.tracker().listFiles()),
                catalog -> {
                    table.getItems().setAll(catalog.files());
                    status.setText(catalog.files().size() + " file(s) on the tracker at " + catalog.tracker());
                },
                error -> {
                    String message = Dialogs.messageOf(error);
                    status.setText("Could not reach the tracker: " + message);
                    if (showAlert) {
                        Dialogs.error("Could not reach the tracker",
                                message + "\n\nCheck the tracker address and port in the Settings tab.");
                    }
                });
    }

    private void downloadSelected() {
        FileSummary file = table.getSelectionModel().getSelectedItem();
        if (file != null && downloads.start(file)) {
            getTabPane().getSelectionModel().select(downloads);
        }
    }
}
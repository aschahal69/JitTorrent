package com.jittorrent.ui;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;

import com.jittorrent.model.LocalFile;
import com.jittorrent.util.SizeFormat;

import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;

public class MyFilesTab extends Tab {
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AppContext ctx;
    private final TableView<LocalFile> table = new TableView<>();
    private final Label status = new Label();
    private final BooleanProperty busy = new SimpleBooleanProperty(false);

    public MyFilesTab(AppContext ctx) {
        super("My Files");
        this.ctx = ctx;
        setClosable(false);

        table.getColumns().add(Tables.textColumn("File", 220, LocalFile::fileName));
        table.getColumns().add(Tables.textColumn("Size", 90, f -> SizeFormat.humanReadable(f.fileSize())));
        table.getColumns().add(Tables.textColumn("Status", 100, f -> f.status().name()));
        table.getColumns().add(Tables.textColumn("Added", 130, f -> f.addedAt().format(WHEN)));
        table.getColumns().add(Tables.textColumn("Location", 340, LocalFile::savePath));
        table.setPlaceholder(new Label("Nothing here yet. Seed a file, or download one."));

        Button seedButton = new Button("Seed a file...");
        seedButton.setOnAction(event -> chooseAndSeed());
        Button seedSelectedButton = new Button("Seed selected");
        seedSelectedButton.setOnAction(event -> seedSelected());
        Button removeButton = new Button("Remove record");
        removeButton.setOnAction(event -> removeSelected());
        Button refreshButton = new Button("Refresh");
        refreshButton.setOnAction(event -> refresh());

        BooleanBinding nothingSelected = table.getSelectionModel().selectedItemProperty().isNull();
        seedButton.disableProperty().bind(busy);
        seedSelectedButton.disableProperty().bind(nothingSelected.or(busy));
        removeButton.disableProperty().bind(nothingSelected);

        HBox bar = new HBox(8, seedButton, seedSelectedButton, removeButton, refreshButton);
        bar.setPadding(new Insets(10));
        status.setPadding(new Insets(6, 10, 6, 10));

        BorderPane root = new BorderPane(table);
        root.setTop(bar);
        root.setBottom(status);
        setContent(root);

        ctx.addDataListener(this::refresh);
        refresh();
    }

    public void refresh() {
        ctx.runAsync(
                () -> ctx.data().listFiles(),
                files -> table.getItems().setAll(files),
                error -> status.setText("Could not read the local database: " + Dialogs.messageOf(error)));
    }

    private void chooseAndSeed() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose a file to share");
        File chosen = chooser.showOpenDialog(getTabPane().getScene().getWindow());
        if (chosen != null) {
            seed(chosen.toPath());
        }
    }

    private void seedSelected() {
        LocalFile selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        Path path = Path.of(selected.savePath());
        if (!Files.isRegularFile(path)) {
            Dialogs.error("File not found", "This file is no longer at:\n" + path);
            return;
        }
        seed(path);
    }

    private void seed(Path file) {
        busy.set(true);
        status.setText("Preparing " + file.getFileName() + " (hashing the pieces)...");
        ctx.runAsync(
                () -> ctx.seedFile(file),
                result -> {
                    busy.set(false);
                    status.setText("Now sharing " + result.meta().getFileName());
                    ctx.fireDataChanged();
                    if (result.trackerWarning() != null) {
                        Dialogs.warning("Sharing, but the tracker was not told",
                                "Other peers cannot find this file until the tracker is reachable.\n"
                                        + "Select the file and press \"Seed selected\" again once it is.\n\n"
                                        + result.trackerWarning());
                    }
                },
                error -> {
                    busy.set(false);
                    status.setText("Could not share the file.");
                    Dialogs.error("Could not share the file", Dialogs.messageOf(error));
                });
    }

    private void removeSelected() {
        LocalFile selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        if (!Dialogs.confirm("Remove this record?",
                "The record is removed from this list. The file on disk is not deleted, and if it is "
                        + "being shared it stays shared until you close the app.")) {
            return;
        }
        ctx.runAsync(
                () -> ctx.data().removeFile(selected.id()),
                removed -> ctx.fireDataChanged(),
                error -> Dialogs.error("Could not remove the record", Dialogs.messageOf(error)));
    }
}
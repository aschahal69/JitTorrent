package com.jittorrent.ui;

import java.time.format.DateTimeFormatter;

import com.jittorrent.model.HistoryEntry;
import com.jittorrent.util.SizeFormat;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;

public class HistoryTab extends Tab {
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AppContext ctx;
    private final TableView<HistoryEntry> table = new TableView<>();
    private final Label status = new Label();

    public HistoryTab(AppContext ctx) {
        super("History");
        this.ctx = ctx;
        setClosable(false);

        table.getColumns().add(Tables.textColumn("Finished", 160, h -> h.finishedAt().format(WHEN)));
        table.getColumns().add(Tables.textColumn("File", 240, HistoryEntry::fileName));
        table.getColumns().add(Tables.textColumn("Size", 90, h -> SizeFormat.humanReadable(h.fileSize())));
        table.getColumns().add(Tables.textColumn("Result", 90, HistoryEntry::result));
        table.getColumns().add(Tables.textColumn("Details", 300, HistoryEntry::detail));
        table.setPlaceholder(new Label("No downloads yet."));

        Button refreshButton = new Button("Refresh");
        refreshButton.setOnAction(event -> refresh());
        Button clearButton = new Button("Clear history");
        clearButton.setOnAction(event -> clear());

        HBox bar = new HBox(8, refreshButton, clearButton);
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
                () -> ctx.data().listHistory(),
                entries -> {
                    table.getItems().setAll(entries);
                    status.setText(entries.size() + " entr" + (entries.size() == 1 ? "y" : "ies"));
                },
                error -> status.setText("Could not read the local database: " + Dialogs.messageOf(error)));
    }

    private void clear() {
        if (!Dialogs.confirm("Clear the download history?", "This cannot be undone. Your files are not affected.")) {
            return;
        }
        ctx.runAsync(
                () -> ctx.data().clearHistory(),
                deleted -> ctx.fireDataChanged(),
                error -> Dialogs.error("Could not clear the history", Dialogs.messageOf(error)));
    }
}
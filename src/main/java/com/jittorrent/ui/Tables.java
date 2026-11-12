package com.jittorrent.ui;

import java.util.function.Function;

import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.TableColumn;

final class Tables {
    private Tables() { }

    static <T> TableColumn<T, String> textColumn(String title, double width, Function<T, String> text) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(text.apply(cell.getValue())));
        column.setPrefWidth(width);
        return column;
    }
}
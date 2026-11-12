package com.jittorrent.ui;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

final class Dialogs {
    private Dialogs() { }

    static void info(String header, String message) {
        show(Alert.AlertType.INFORMATION, header, message);
    }

    static void warning(String header, String message) {
        show(Alert.AlertType.WARNING, header, message);
    }

    static void error(String header, String message) {
        show(Alert.AlertType.ERROR, header, message);
    }

    static boolean confirm(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        alert.setTitle("MiniTorrent");
        alert.setHeaderText(header);
        return alert.showAndWait().filter(button -> button == ButtonType.OK).isPresent();
    }

    /** Some exceptions have no message, so fall back to the class name. */
    static String messageOf(Throwable error) {
        if (error == null) return "Unknown error";
        String message = error.getMessage();
        return (message == null || message.isBlank()) ? error.getClass().getSimpleName() : message;
    }

    private static void show(Alert.AlertType type, String header, String message) {
        Alert alert = new Alert(type);
        alert.setTitle("MiniTorrent");
        alert.setHeaderText(header);
        alert.setContentText(message);
        alert.showAndWait();
    }
}

package com.jittorrent.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseConnection {

    private static final String[] TRACKER_SCHEMA = {
        """
        CREATE TABLE IF NOT EXISTS shared_files (
            file_id     INTEGER PRIMARY KEY AUTOINCREMENT,
            file_hash   TEXT    NOT NULL UNIQUE,
            file_name   TEXT    NOT NULL,
            file_size   INTEGER NOT NULL,
            piece_size  INTEGER NOT NULL,
            piece_count INTEGER NOT NULL,
            created_at  TEXT    NOT NULL
        )""",
        """
        CREATE TABLE IF NOT EXISTS file_pieces (
            file_id     INTEGER NOT NULL REFERENCES shared_files(file_id) ON DELETE CASCADE,
            piece_index INTEGER NOT NULL,
            piece_hash  TEXT    NOT NULL,
            PRIMARY KEY (file_id, piece_index)
        )""",
        """
        CREATE TABLE IF NOT EXISTS peers (
            peer_id    INTEGER PRIMARY KEY AUTOINCREMENT,
            ip_address TEXT    NOT NULL,
            port       INTEGER NOT NULL,
            last_seen  TEXT    NOT NULL,
            UNIQUE (ip_address, port)
        )""",
        """
        CREATE TABLE IF NOT EXISTS seeds (
            peer_id INTEGER NOT NULL REFERENCES peers(peer_id) ON DELETE CASCADE,
            file_id INTEGER NOT NULL REFERENCES shared_files(file_id) ON DELETE CASCADE,
            PRIMARY KEY (peer_id, file_id)
        )"""
    };

    private static final String[] PEER_SCHEMA = {
        """
        CREATE TABLE IF NOT EXISTS local_files (
            local_id  INTEGER PRIMARY KEY AUTOINCREMENT,
            file_hash TEXT    NOT NULL,
            file_name TEXT    NOT NULL,
            file_size INTEGER NOT NULL,
            save_path TEXT    NOT NULL,
            status    TEXT    NOT NULL,
            added_at  TEXT    NOT NULL,
            UNIQUE (file_hash, save_path)
        )""",
        """
        CREATE TABLE IF NOT EXISTS download_history (
            history_id  INTEGER PRIMARY KEY AUTOINCREMENT,
            file_name   TEXT    NOT NULL,
            file_size   INTEGER NOT NULL,
            file_hash   TEXT    NOT NULL,
            started_at  TEXT    NOT NULL,
            finished_at TEXT    NOT NULL,
            result      TEXT    NOT NULL,
            detail      TEXT    NOT NULL
        )""",
        """
        CREATE TABLE IF NOT EXISTS settings (
            setting_key   TEXT PRIMARY KEY,
            setting_value TEXT NOT NULL
        )"""
    };

    private final String url;
    private final String[] schema;

    // tracker database is public
    public DatabaseConnection(String dbPath) {
        this(dbPath, TRACKER_SCHEMA);
    }

    private DatabaseConnection(String dbPath, String[] schema) {
        this.url = "jdbc:sqlite:" + dbPath;
        this.schema = schema;
    }

    // even peer database is public
    public static DatabaseConnection forPeer(String dbPath) {
        return new DatabaseConnection(dbPath, PEER_SCHEMA);
    }

    /** A new connection per operation: simple, and safe when several threads work at once. */
    public Connection open() throws SQLException {
        Connection c = DriverManager.getConnection(url);
        try (Statement st = c.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
            st.execute("PRAGMA busy_timeout = 5000");
        }
        return c;
    }

    public void initSchema() throws SQLException {
        try (Connection c = open(); Statement st = c.createStatement()) {
            for (String sql : schema) {
                st.execute(sql);
            }
        }
    }
}
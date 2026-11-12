package com.jittorrent.model;

import java.time.LocalDateTime;

public record HistoryEntry(long id, String fileName, long fileSize, String fileHash,
                           LocalDateTime startedAt, LocalDateTime finishedAt,
                           String result, String detail) { }
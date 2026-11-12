package com.jittorrent.model;

import java.time.LocalDateTime;

public record LocalFile(long id, String fileHash, String fileName, long fileSize,
                        String savePath, LocalFileStatus status, LocalDateTime addedAt) { }
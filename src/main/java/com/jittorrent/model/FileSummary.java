package com.jittorrent.model;

public record FileSummary(String fileName, long fileSize, String fileHash, int pieceCount) { }
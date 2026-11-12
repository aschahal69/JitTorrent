package com.jittorrent.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.jittorrent.model.FileSummary;

public class FileListMessage extends Message {
    private final List<FileSummary> files;

    public FileListMessage(List<FileSummary> files) { this.files = List.copyOf(files); }

    public List<FileSummary> getFiles() { return files; }

    @Override public MessageType type() { return MessageType.FILE_LIST; }

    @Override protected void writeBody(DataOutputStream out) throws IOException {
        out.writeInt(files.size());
        for (FileSummary f : files) {
            out.writeUTF(f.fileName());
            out.writeLong(f.fileSize());
            out.writeUTF(f.fileHash());
            out.writeInt(f.pieceCount());
        }
    }

    static FileListMessage readBody(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > MAX_LIST_ENTRIES) {
            throw new InvalidMessageException("Invalid file count: " + count);
        }
        List<FileSummary> files = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            files.add(new FileSummary(in.readUTF(), in.readLong(), in.readUTF(), in.readInt()));
        }
        return new FileListMessage(files);
    }
}
package com.jittorrent.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.jittorrent.model.FileMetadata;

public class MetadataMessage extends Message {

    // metadata object for 1 file is constant
    private final FileMetadata metadata;

    public MetadataMessage(FileMetadata metadata) { this.metadata = metadata; }

    public FileMetadata getMetadata() { return metadata; }

    @Override public MessageType type() { return MessageType.METADATA; }

    @Override protected void writeBody(DataOutputStream out) throws IOException {
        writeMetadata(out, metadata);
    }

    static MetadataMessage readBody(DataInputStream in) throws IOException {
        return new MetadataMessage(readMetadata(in));
    }

    // this method prints the metadata objects contents
    static void writeMetadata(DataOutputStream out, FileMetadata metadata) throws IOException {
        out.writeUTF(metadata.getFileName());
        out.writeLong(metadata.getFileSize());
        out.writeUTF(metadata.getFileHash());
        out.writeInt(metadata.getPieceSize());
        out.writeInt(metadata.getPieceCount());
        for (int i = 0; i < metadata.getPieceCount(); i++) {
            out.writeUTF(metadata.getPieceHash(i));
        }
    }

    // no need to instantiate a metadata object to jst read one
    static FileMetadata readMetadata(DataInputStream in) throws IOException {
        String name = in.readUTF();
        long size = in.readLong();
        String fileHash = in.readUTF();
        int pieceSize = in.readInt();
        int count = in.readInt();
        if (count <= 0 || count > MAX_PIECES || pieceSize <= 0 || size <= 0) {
            throw new InvalidMessageException("Invalid metadata values");
        }

        // add the hash hexadecimal strings to the hashes arraylist
        List<String> hashes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            hashes.add(in.readUTF());
        }
        return new FileMetadata(name, size, fileHash, pieceSize, hashes);
    }
}
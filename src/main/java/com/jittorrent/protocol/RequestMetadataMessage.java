package com.jittorrent.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class RequestMetadataMessage extends Message {
    private final String fileHash;

    public RequestMetadataMessage(String fileHash) { this.fileHash = fileHash; }

    public String getFileHash() { return fileHash; }

    @Override public MessageType type() { return MessageType.REQUEST_METADATA; }

    @Override protected void writeBody(DataOutputStream out) throws IOException {
        out.writeUTF(fileHash);
    }

    static RequestMetadataMessage readBody(DataInputStream in) throws IOException {
        return new RequestMetadataMessage(in.readUTF());
    }
}
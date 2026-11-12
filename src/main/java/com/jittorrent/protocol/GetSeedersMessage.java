package com.jittorrent.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class GetSeedersMessage extends Message {
    private final String fileHash;

    public GetSeedersMessage(String fileHash) { this.fileHash = fileHash; }

    public String getFileHash() { return fileHash; }

    @Override public MessageType type() { return MessageType.GET_SEEDERS; }

    @Override protected void writeBody(DataOutputStream out) throws IOException {
        out.writeUTF(fileHash);
    }

    static GetSeedersMessage readBody(DataInputStream in) throws IOException {
        return new GetSeedersMessage(in.readUTF());
    }
}
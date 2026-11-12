package com.jittorrent.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import com.jittorrent.model.FileMetadata;

public class RegisterFileMessage extends Message {
    private final FileMetadata metadata;
    private final int peerPort;

    public RegisterFileMessage(FileMetadata metadata, int peerPort) {
        this.metadata = metadata;
        this.peerPort = peerPort;
    }

    public FileMetadata getMetadata() { return metadata; }
    public int getPeerPort() { return peerPort; }

    @Override public MessageType type() { return MessageType.REGISTER_FILE; }

    @Override protected void writeBody(DataOutputStream out) throws IOException {
        MetadataMessage.writeMetadata(out, metadata);
        out.writeInt(peerPort);
    }

    static RegisterFileMessage readBody(DataInputStream in) throws IOException {
        FileMetadata meta = MetadataMessage.readMetadata(in);
        return new RegisterFileMessage(meta, in.readInt());
    }
}
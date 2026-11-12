package com.jittorrent.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class RequestPieceMessage extends Message {
    private final String fileHash;
    private final int pieceIndex;

    public RequestPieceMessage(String fileHash, int pieceIndex) {
        this.fileHash = fileHash;
        this.pieceIndex = pieceIndex;
    }

    public String getFileHash() { return fileHash; }
    public int getPieceIndex() { return pieceIndex; }

    @Override public MessageType type() { return MessageType.REQUEST_PIECE; }

    @Override protected void writeBody(DataOutputStream out) throws IOException {
        out.writeUTF(fileHash);
        out.writeInt(pieceIndex);
    }

    static RequestPieceMessage readBody(DataInputStream in) throws IOException {
        return new RequestPieceMessage(in.readUTF(), in.readInt());
    }
}
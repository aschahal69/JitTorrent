package com.jittorrent.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class PieceDataMessage extends Message {
    private final String fileHash;
    private final int pieceIndex;
    private final byte[] data;

    public PieceDataMessage(String fileHash, int pieceIndex, byte[] data) {
        this.fileHash = fileHash;
        this.pieceIndex = pieceIndex;
        this.data = data;
    }

    public String getFileHash() { return fileHash; }
    public int getPieceIndex() { return pieceIndex; }
    public byte[] getData() { return data; }

    @Override public MessageType type() { return MessageType.PIECE_DATA; }

    @Override protected void writeBody(DataOutputStream out) throws IOException {
        out.writeUTF(fileHash);
        out.writeInt(pieceIndex);
        out.writeInt(data.length);
        out.write(data);
    }

    static PieceDataMessage readBody(DataInputStream in) throws IOException {
        String fileHash = in.readUTF();
        int index = in.readInt();
        int length = in.readInt();
        if (length < 0 || length > MAX_PIECE_BYTES) {
            throw new InvalidMessageException("Piece length out of range: " + length);
        }
        byte[] data = new byte[length];
        in.readFully(data);
        return new PieceDataMessage(fileHash, index, data);
    }
}
package com.jittorrent.storage;
//checked exception on purpose, so callers are forced to handle a corrupted piece by requesting it again.
public class HashMismatchException extends Exception {
    
    private final int pieceIndex;

    public HashMismatchException(int pieceIndex) {
        super("Piece " + pieceIndex + " failed hash verification");
        this.pieceIndex = pieceIndex;
    }

    public int getPieceIndex() { return pieceIndex; }

}

//This describes a shared file. Later it goes over the network and into the tracker database

package com.jittorrent.model;

// since 1 file has 1 metadata we can assign serial number to the metadata class's objects.
// bittorrent protocol favours serial numbers of a files metadata since it may end up on a tracker server
import java.io.Serializable;
import java.util.List;

public class FileMetadata implements Serializable {

    private static final long serialVersionUID = 1L;

    // this is the standard naming convention for metadata of files

    private final String fileName;
    private final long fileSize;
    private final String fileHash;
    private final int pieceSize;
    private final List<String> pieceHashes;

    public FileMetadata(String fileName, long fileSize, String fileHash,
                        int pieceSize, List<String> pieceHashes) {
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.fileHash = fileHash;
        this.pieceSize = pieceSize;
        this.pieceHashes = List.copyOf(pieceHashes);
    }

    public String getFileName() { return fileName; }
    public long getFileSize() { return fileSize; }
    public String getFileHash() { return fileHash; }
    public int getPieceSize() { return pieceSize; }
    public int getPieceCount() { return pieceHashes.size(); }

    public String getPieceHash(int index) { return pieceHashes.get(index); }

    // every piece except the last one is of equal length. The last piece holds whatever is left.
    public int pieceLength(int index) {
        if (index == getPieceCount() - 1) {
            return (int) (fileSize - (long) index * pieceSize);
        }
        return pieceSize;
    }
    
}

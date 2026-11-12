package com.jittorrent.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

// A template class
public abstract class Message {

    // Safety limit so a bad length value cannot make us allocate gigabytes.
    public static final int MAX_PIECE_BYTES = 4 * 1024 * 1024;
    public static final int MAX_PIECES = 1_000_000;
    public static final int MAX_LIST_ENTRIES = 100_000;


    public abstract MessageType type();

    protected abstract void writeBody(DataOutputStream out) throws IOException;

    /** Every message is: one type byte, then its own fields. */
    public final void write(DataOutputStream out) throws IOException {
        out.writeByte(type().code());
        writeBody(out);
        out.flush();
    }

    /** Factory: reads the type byte, then lets the right subclass read its fields. */
    // the switch case must cover every enum code or else it wont compile
    public static Message read(DataInputStream in) throws IOException {
        MessageType type = MessageType.fromCode(in.readUnsignedByte());
        return switch (type) {

            // field - filehash , sent by leecher, it is asking tell me about this file
            case REQUEST_METADATA -> RequestMetadataMessage.readBody(in);

            // fields - name, size, fileHash, pieceSize, pieceHashes, sent by seeder, as a response to leecher  
            case METADATA         -> MetadataMessage.readBody(in);

            // fields - fileHash, pieceIndex , sent by leecher , it is asking to send me a piece n
            case REQUEST_PIECE    -> RequestPieceMessage.readBody(in);

            // fields - fileHash, pieceIndex, bytes, sent by seeder, it is the piece of the requested file
            case PIECE_DATA       -> PieceDataMessage.readBody(in);

            // there is always an error
            case ERROR            -> ErrorMessage.readBody(in);

            
            case REGISTER_FILE    -> RegisterFileMessage.readBody(in);

            case LIST_FILES       -> ListFilesMessage.readBody(in);

            case FILE_LIST        -> FileListMessage.readBody(in);

            case GET_SEEDERS      -> GetSeedersMessage.readBody(in);

            case SEEDER_LIST      -> SeederListMessage.readBody(in);

            case OK               -> OkMessage.readBody(in);

        };
    }
}
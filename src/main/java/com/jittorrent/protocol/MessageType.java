package com.jittorrent.protocol;

// a class whose constants are known to compiler at compile time
// Bittorrent protocol states these type of messages must be there
public enum MessageType {
    REQUEST_METADATA(1),
    METADATA(2),
    REQUEST_PIECE(3),
    PIECE_DATA(4),
    ERROR(5),
    REGISTER_FILE(6),
    LIST_FILES(7),
    FILE_LIST(8),
    GET_SEEDERS(9),
    SEEDER_LIST(10),
    OK(11);

    // a message object must never be changed
    private final int code;

    MessageType(int code) { this.code = code; }

    public int code() { return code; }

    
    public static MessageType fromCode(int code) throws InvalidMessageException {
        for (MessageType t : values()) {
            if (t.code == code) return t;
        }
        throw new InvalidMessageException("Unknown message type: " + code);
    }
}
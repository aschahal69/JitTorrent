package com.jittorrent.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import com.jittorrent.model.FileMetadata;

class MessageTest {

    private Message roundTrip(Message original) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        original.write(new DataOutputStream(bytes));
        return Message.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
    }

    @Test
    void requestPieceSurvivesRoundTrip() throws IOException {
        Message copy = roundTrip(new RequestPieceMessage("abc123", 7));
        RequestPieceMessage m = assertInstanceOf(RequestPieceMessage.class, copy);
        assertEquals("abc123", m.getFileHash());
        assertEquals(7, m.getPieceIndex());
    }

    @Test
    void pieceDataSurvivesRoundTrip() throws IOException {
        byte[] payload = {1, 2, 3, (byte) 250, 0, 9};
        PieceDataMessage m = assertInstanceOf(PieceDataMessage.class,
                roundTrip(new PieceDataMessage("abc123", 3, payload)));
        assertEquals(3, m.getPieceIndex());
        assertArrayEquals(payload, m.getData());
    }

    @Test
    void metadataSurvivesRoundTrip() throws IOException {
        FileMetadata meta = new FileMetadata("movie.mp4", 1000L, "filehash", 512,
                List.of("hash0", "hash1"));
        MetadataMessage m = assertInstanceOf(MetadataMessage.class,
                roundTrip(new MetadataMessage(meta)));
        FileMetadata copy = m.getMetadata();
        assertEquals("movie.mp4", copy.getFileName());
        assertEquals(1000L, copy.getFileSize());
        assertEquals(2, copy.getPieceCount());
        assertEquals("hash1", copy.getPieceHash(1));
    }

    @Test
    void requestMetadataAndErrorSurviveRoundTrip() throws IOException {
        assertEquals("h", assertInstanceOf(RequestMetadataMessage.class,
                roundTrip(new RequestMetadataMessage("h"))).getFileHash());
        assertEquals("no such file", assertInstanceOf(ErrorMessage.class,
                roundTrip(new ErrorMessage("no such file"))).getText());
    }

    @Test
    void unknownTypeIsRejected() {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(new byte[]{99}));
        assertThrows(InvalidMessageException.class, () -> Message.read(in));
    }

    @Test
    void oversizedPieceLengthIsRejected() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(MessageType.PIECE_DATA.code());
        out.writeUTF("h");
        out.writeInt(0);
        out.writeInt(Integer.MAX_VALUE); // claims a 2 GB piece
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        assertThrows(InvalidMessageException.class, () -> Message.read(in));
    }
}
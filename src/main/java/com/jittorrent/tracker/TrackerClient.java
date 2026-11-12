// this is used by seeders and leechers to talk to tracker

package com.jittorrent.tracker;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;

import com.jittorrent.model.FileMetadata;
import com.jittorrent.model.FileSummary;
import com.jittorrent.model.PeerAddress;
import com.jittorrent.protocol.ErrorMessage;
import com.jittorrent.protocol.FileListMessage;
import com.jittorrent.protocol.GetSeedersMessage;
import com.jittorrent.protocol.InvalidMessageException;
import com.jittorrent.protocol.ListFilesMessage;
import com.jittorrent.protocol.Message;
import com.jittorrent.protocol.MetadataMessage;
import com.jittorrent.protocol.OkMessage;
import com.jittorrent.protocol.RegisterFileMessage;
import com.jittorrent.protocol.RequestMetadataMessage;
import com.jittorrent.protocol.SeederListMessage;

public class TrackerClient {
    private static final int TIMEOUT_MS = 10_000;

    private final String host;
    private final int port;

    public TrackerClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void registerFile(FileMetadata meta, int peerPort) throws IOException {
        call(new RegisterFileMessage(meta, peerPort), OkMessage.class);
    }

    public List<FileSummary> listFiles() throws IOException {
        return call(new ListFilesMessage(), FileListMessage.class).getFiles();
    }

    public FileMetadata getMetadata(String fileHash) throws IOException {
        return call(new RequestMetadataMessage(fileHash), MetadataMessage.class).getMetadata();
    }

    public List<PeerAddress> getSeeders(String fileHash) throws IOException {
        return call(new GetSeedersMessage(fileHash), SeederListMessage.class).getSeeders();
    }

    /** One short-lived connection per request; the generic type says what reply we expect. */
    private <T extends Message> T call(Message request, Class<T> expected) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), TIMEOUT_MS);
            socket.setSoTimeout(TIMEOUT_MS);
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));

            request.write(out);
            Message reply = Message.read(in);

            if (reply instanceof ErrorMessage e) {
                throw new IOException("Tracker error: " + e.getText());
            }
            if (!expected.isInstance(reply)) {
                throw new InvalidMessageException("Unexpected reply from tracker: " + reply.type());
            }
            return expected.cast(reply);
        }
    }
}
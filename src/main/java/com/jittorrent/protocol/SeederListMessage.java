package com.jittorrent.protocol;

import com.jittorrent.model.PeerAddress;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SeederListMessage extends Message {
    private final List<PeerAddress> seeders;

    public SeederListMessage(List<PeerAddress> seeders) { this.seeders = List.copyOf(seeders); }

    public List<PeerAddress> getSeeders() { return seeders; }

    @Override public MessageType type() { return MessageType.SEEDER_LIST; }

    @Override protected void writeBody(DataOutputStream out) throws IOException {
        out.writeInt(seeders.size());
        for (PeerAddress p : seeders) {
            out.writeUTF(p.host());
            out.writeInt(p.port());
        }
    }

    static SeederListMessage readBody(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > MAX_LIST_ENTRIES) {
            throw new InvalidMessageException("Invalid seeder count: " + count);
        }
        List<PeerAddress> seeders = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            seeders.add(new PeerAddress(in.readUTF(), in.readInt()));
        }
        return new SeederListMessage(seeders);
    }
}
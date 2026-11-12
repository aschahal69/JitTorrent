package com.jittorrent.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;

public class OkMessage extends Message {
    @Override public MessageType type() { return MessageType.OK; }

    @Override protected void writeBody(DataOutputStream out) { /* no fields */ }

    static OkMessage readBody(DataInputStream in) { return new OkMessage(); }
}
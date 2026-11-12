package com.jittorrent.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class ErrorMessage extends Message {
    private final String text;

    public ErrorMessage(String text) { this.text = text; }

    public String getText() { return text; }

    @Override public MessageType type() { return MessageType.ERROR; }

    @Override protected void writeBody(DataOutputStream out) throws IOException {
        out.writeUTF(text);
    }

    static ErrorMessage readBody(DataInputStream in) throws IOException {
        return new ErrorMessage(in.readUTF());
    }
}
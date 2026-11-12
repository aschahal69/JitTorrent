package com.jittorrent.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;

public class ListFilesMessage extends Message {
    @Override public MessageType type() { return MessageType.LIST_FILES; }

    @Override protected void writeBody(DataOutputStream out) { //no fields
         }

    static ListFilesMessage readBody(DataInputStream in) { return new ListFilesMessage(); }
}
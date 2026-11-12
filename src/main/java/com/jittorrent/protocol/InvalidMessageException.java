package com.jittorrent.protocol;

import java.io.IOException;

public class InvalidMessageException extends IOException {
    public InvalidMessageException(String message) { super(message); }
}

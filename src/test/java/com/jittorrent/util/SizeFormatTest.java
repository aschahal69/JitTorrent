package com.jittorrent.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class SizeFormatTest {
    @Test
    void formatsCommonSizes() {
        assertEquals("0 B", SizeFormat.humanReadable(0));
        assertEquals("1023 B", SizeFormat.humanReadable(1023));
        assertEquals("1.0 KB", SizeFormat.humanReadable(1024));
        assertEquals("1.4 MB", SizeFormat.humanReadable(1_500_000));
        assertEquals("2.0 GB", SizeFormat.humanReadable(2L * 1024 * 1024 * 1024));
    }
}
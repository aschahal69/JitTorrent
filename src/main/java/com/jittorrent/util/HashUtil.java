// This class calculates the info_hash of byte arrays as well as files.
// The info_hash is the SHA-1 hash of the info dictionary of a .torrent file.
// It is like a primary key that the tracker and peers use to keep track of the file that is being shared.

// Overall process is - Files are split into pieces of equal length (except for the last piece). And this piece is then passed through a SHA1 hash function.
// So when one peer downloads a piece of the file they need, it informs the rest of the peers that “I have this piece” so that the rest of the peers can start downloading that piece from this peer. And the same way, every other peer does the same thing.
package com.jittorrent.util;

// for byte streams and handle input/output errors.
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class HashUtil {

    // constructor so that there is only one instance of this class
    private HashUtil(){}

    // method creates and returns a SHA-1 hashing engine.
    // heavylifting is done by this api only no object is needed. A function call loads the engine and then we can use objects of that engine.
    public static MessageDigest newSha1() {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 not available", e);
        }
    }

    // Hash the first 'length' bytes of the byte array (the last piece may not fill the buffer).
    public static String sha1(byte[] data, int length) {

        // Create a new SHA-1 hashing object and update it with the data to be hashed.each time
        MessageDigest md = newSha1();

        //feed bytes into the hashing engine.
        // BitTorrent divides files into pieces. The final piece may be smaller than the standard piece size.Suppose your buffer has 16 KB capacity, but the last piece contains only 5 KB.
        md.update(data, 0, length);

        // hash value returned in hexadecimal.
        return HexFormat.of().formatHex(md.digest());
    }
    
    // method overloading
    public static String sha1(byte[] data) {
        return sha1(data, data.length);
    }

    // Hash a whole file by streaming it, so large files never sit fully in memory. feed path of file to the function and it returns hash of chunks in loop
    public static String sha1OfFile(Path file) throws IOException {
        MessageDigest md = newSha1();

        // buffer to read file in chunks. chunk size used is standard
        byte[] buf = new byte[8192];
        try (InputStream in = Files.newInputStream(file)) {
            int n;
            while ((n = in.read(buf)) != -1) {
                md.update(buf, 0, n);
            }
        }
        // keep on returning the hash of pieces of file until the whole file is read
        return HexFormat.of().formatHex(md.digest());
    }
}
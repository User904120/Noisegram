package org.cleargram.spi;

import java.io.IOException;
import java.io.InputStream;

/**
 * Locally available complete video content for Duplicate Video hashing.
 *
 * <p>Opening a stream never starts a download or network request. Each call
 * returns a new stream positioned at the beginning of the complete content;
 * the caller must close that stream. This SPI exposes no file path, URI,
 * Android, or Telegram types.</p>
 */
public interface DuplicateVideoReadableSource {

    InputStream openStream() throws IOException;
}

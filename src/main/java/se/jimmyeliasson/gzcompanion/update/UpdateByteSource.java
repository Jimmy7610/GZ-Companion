package se.jimmyeliasson.gzcompanion.update;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.LongConsumer;

/**
 * The network seam for the actual installer byte download - separate from
 * {@link UpdateReleaseSource} (which only ever fetches small JSON text) so {@link UpdateDownloader}
 * can be unit-tested with a fake that just copies a local fixture file, never touching the network.
 */
public interface UpdateByteSource {
    /**
     * Streams {@code url} to {@code destination} (overwriting it), invoking
     * {@code progressCallback} with the running total of bytes written so far as they arrive.
     */
    void download(String url, Path destination, LongConsumer progressCallback) throws IOException;
}

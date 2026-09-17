package org.omnifile.poc.media3;

import android.content.ContentResolver;
import android.net.Uri;

import androidx.media3.common.C;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;

import java.io.IOException;
import java.io.InputStream;

/**
 * A deliberately sequential source: every open must start at offset zero and
 * the stream cannot be repositioned. A player seek therefore exercises a real
 * unsupported-seek boundary instead of silently pretending the source seeks.
 */
final class SequentialDataSource implements DataSource {
    private final ContentResolver resolver;
    private final Uri sourceUri;
    private InputStream input;
    private Uri openedUri;

    SequentialDataSource(ContentResolver resolver, Uri sourceUri) {
        this.resolver = resolver;
        this.sourceUri = sourceUri;
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        if (dataSpec.position != 0) {
            throw new IOException("P05-003 sequential source cannot seek to " + dataSpec.position);
        }
        input = resolver.openInputStream(sourceUri);
        if (input == null) {
            throw new IOException("P05-003 source could not be opened");
        }
        openedUri = dataSpec.uri;
        return dataSpec.length == C.LENGTH_UNSET ? C.LENGTH_UNSET : dataSpec.length;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        if (length == 0) {
            return 0;
        }
        if (input == null) {
            throw new IOException("P05-003 sequential source is not open");
        }
        int count = input.read(buffer, offset, length);
        return count == -1 ? C.RESULT_END_OF_INPUT : count;
    }

    @Override
    public Uri getUri() {
        return openedUri;
    }

    @Override
    public void addTransferListener(androidx.media3.datasource.TransferListener transferListener) {
        // This PoC has no transfer instrumentation beyond Media3 callbacks.
    }

    @Override
    public void close() throws IOException {
        if (input != null) {
            input.close();
            input = null;
        }
        openedUri = null;
    }

    static final class Factory implements DataSource.Factory {
        private final ContentResolver resolver;
        private final Uri sourceUri;

        Factory(ContentResolver resolver, Uri sourceUri) {
            this.resolver = resolver;
            this.sourceUri = sourceUri;
        }

        @Override
        public DataSource createDataSource() {
            return new SequentialDataSource(resolver, sourceUri);
        }
    }
}

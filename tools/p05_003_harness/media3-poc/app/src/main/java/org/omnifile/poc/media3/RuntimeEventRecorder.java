package org.omnifile.poc.media3;

import android.content.Context;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Locale;
import java.util.regex.Pattern;

final class RuntimeEventRecorder {
    enum Kind {
        SOURCE_RESOLVED,
        SOURCE_RE_RESOLVED,
        PREPARE,
        START,
        PLAYBACK,
        DURATION,
        SEEK,
        EOF,
        STOP,
        REOPEN,
        PLAYER_RECREATED,
        FAILURE
    }

    private static final String TAG = "P05-003";
    private static final Pattern SECRET_QUERY = Pattern.compile(
            "(?i)([?&](?:token|access_token|sig|signature|authorization)=)[^&\\s]+"
    );

    private final File output;
    private int sequence;

    RuntimeEventRecorder(Context context) {
        output = new File(context.getFilesDir(), "p05_003_runtime.jsonl");
    }

    synchronized void record(Kind kind, String source, long positionMs, long durationMs, String detail) {
        String safeSource = SECRET_QUERY.matcher(source == null ? "" : source)
                .replaceAll("$1<redacted>");
        String safeDetail = SECRET_QUERY.matcher(detail == null ? "" : detail)
                .replaceAll("$1<redacted>");
        try {
            JSONObject event = new JSONObject();
            event.put("sequence", sequence++);
            event.put("kind", kind.name().toLowerCase(Locale.US));
            event.put("source", safeSource);
            event.put("positionMs", positionMs);
            event.put("durationMs", durationMs);
            event.put("detail", safeDetail);
            try (FileWriter writer = new FileWriter(output, true)) {
                writer.write(event.toString());
                writer.write('\n');
            }
            Log.d(TAG, event.toString());
        } catch (JSONException | IOException error) {
            Log.e(TAG, "event recorder failure", error);
        }
    }

    File output() {
        return output;
    }
}

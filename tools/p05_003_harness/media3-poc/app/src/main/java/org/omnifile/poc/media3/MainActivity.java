package org.omnifile.poc.media3;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class MainActivity extends Activity {
    private static final int OPEN_TREE = 1001;

    private RuntimeEventRecorder recorder;
    private ExoPlayer player;
    private Uri currentUri;
    private String currentSource = "";
    private TextView status;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        recorder = new RuntimeEventRecorder(this);
        buildUi();
        createPlayer(false);
        chooseLocalSource();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);

        status = new TextView(this);
        status.setText("P05-003 disposable Media3 runtime harness");
        root.addView(status, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addButton(root, "Use local WAV fixture", v -> chooseLocalSource());
        addButton(root, "Choose SAF tree", v -> chooseSafTree());
        addButton(root, "Play direct Media3 source", v -> prepareAndPlay(false));
        addButton(root, "Play sequential DataSource", v -> prepareAndPlay(true));
        addButton(root, "Seek to 50%", v -> seekHalf());
        addButton(root, "Stop", v -> stopPlayback());
        addButton(root, "Reopen current source", v -> reopen());
        addButton(root, "Recreate player + re-resolve", v -> recreatePlayer());

        TextView hint = new TextView(this);
        hint.setText("JSONL: " + recorder.output().getAbsolutePath());
        root.addView(hint, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(root);
    }

    private void addButton(LinearLayout root, String label, android.view.View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(listener);
        root.addView(button, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void createPlayer(boolean recreated) {
        player = new ExoPlayer.Builder(this).build();
        player.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (isPlaying) {
                    recorder.record(RuntimeEventRecorder.Kind.PLAYBACK, currentSource,
                            player == null ? -1 : player.getCurrentPosition(),
                            player == null ? -1 : player.getDuration(), "playing");
                }
            }

            @Override
            public void onTimelineChanged(androidx.media3.common.Timeline timeline, int reason) {
                if (player != null && player.getDuration() != C.TIME_UNSET) {
                    recorder.record(RuntimeEventRecorder.Kind.DURATION, currentSource,
                            player.getCurrentPosition(), player.getDuration(), "timeline=" + reason);
                }
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_ENDED) {
                    recorder.record(RuntimeEventRecorder.Kind.EOF, currentSource,
                            player == null ? -1 : player.getCurrentPosition(),
                            player == null ? -1 : player.getDuration(), "state_ended");
                }
            }

            @Override
            public void onPlayerError(androidx.media3.common.PlaybackException error) {
                recorder.record(RuntimeEventRecorder.Kind.FAILURE, currentSource,
                        player == null ? -1 : player.getCurrentPosition(),
                        player == null ? -1 : player.getDuration(),
                        error.getErrorCodeName() + ":" + error.getClass().getSimpleName() + ":" + error.getMessage());
                setStatus("failure: " + error.getErrorCodeName());
            }
        });
        if (recreated) {
            recorder.record(RuntimeEventRecorder.Kind.PLAYER_RECREATED, currentSource, -1, -1,
                    "new ExoPlayer instance");
        }
    }

    private void chooseLocalSource() {
        try {
            File fixture = new File(getFilesDir(), "p05_003_fixture.wav");
            if (!fixture.exists()) {
                writeWav(fixture);
            }
            currentUri = Uri.fromFile(fixture);
            currentSource = "local-file";
            recorder.record(RuntimeEventRecorder.Kind.SOURCE_RESOLVED, currentSource, -1, -1,
                    "deterministic app-private WAV fixture");
            setStatus("source: local WAV");
        } catch (IOException error) {
            recordFailure("local fixture", error);
        }
    }

    private void chooseSafTree() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(intent, OPEN_TREE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != OPEN_TREE || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri tree = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(tree,
                    data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Uri child = firstPlayableChild(tree);
            if (child == null) {
                recordFailure("saf-tree", new IOException("selected tree has no immediate playable child"));
                return;
            }
            currentUri = child;
            currentSource = "saf-tree-child";
            recorder.record(RuntimeEventRecorder.Kind.SOURCE_RESOLVED, currentSource, -1, -1,
                    "ACTION_OPEN_DOCUMENT_TREE child resolved");
            setStatus("source: SAF tree child");
        } catch (Exception error) {
            recordFailure("saf-tree", error);
        }
    }

    private Uri firstPlayableChild(Uri tree) {
        String treeId = DocumentsContract.getTreeDocumentId(tree);
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId);
        String[] projection = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
        };
        try (Cursor cursor = getContentResolver().query(children, projection, null, null, null)) {
            if (cursor == null) return null;
            int idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
            int mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE);
            while (cursor.moveToNext()) {
                String mime = cursor.getString(mimeIndex);
                if (mime != null && (mime.startsWith("audio/") || mime.startsWith("video/")
                        || mime.equals("application/ogg"))) {
                    return DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(idIndex));
                }
            }
        }
        return null;
    }

    private void prepareAndPlay(boolean sequential) {
        if (currentUri == null || player == null) {
            recordFailure("prepare", new IOException("no source selected"));
            return;
        }
        try {
            MediaItem item = MediaItem.fromUri(currentUri);
            if (sequential) {
                MediaSource source = new ProgressiveMediaSource.Factory(
                        new SequentialDataSource.Factory(getContentResolver(), currentUri)).createMediaSource(item);
                player.setMediaSource(source);
            } else {
                player.setMediaItem(item);
            }
            recorder.record(RuntimeEventRecorder.Kind.PREPARE, currentSource, -1, -1,
                    sequential ? "sequential" : "direct");
            player.prepare();
            recorder.record(RuntimeEventRecorder.Kind.START, currentSource, -1, -1,
                    sequential ? "sequential" : "direct");
            player.play();
            setStatus("playing: " + currentSource + (sequential ? " (sequential)" : " (direct)"));
        } catch (Exception error) {
            recordFailure("prepare", error);
        }
    }

    private void seekHalf() {
        if (player == null || player.getDuration() == C.TIME_UNSET || player.getDuration() <= 0) {
            recordFailure("seek", new IOException("duration unavailable"));
            return;
        }
        long target = player.getDuration() / 2;
        recorder.record(RuntimeEventRecorder.Kind.SEEK, currentSource, target, player.getDuration(), "requested_50_percent");
        player.seekTo(target);
    }

    private void stopPlayback() {
        if (player != null) {
            player.stop();
            recorder.record(RuntimeEventRecorder.Kind.STOP, currentSource,
                    player.getCurrentPosition(), player.getDuration(), "user_stop");
        }
    }

    private void reopen() {
        recorder.record(RuntimeEventRecorder.Kind.REOPEN, currentSource, -1, -1, "reprepare_current_source");
        prepareAndPlay(false);
    }

    private void recreatePlayer() {
        if (player != null) {
            player.release();
        }
        createPlayer(true);
        if (currentUri != null) {
            recorder.record(RuntimeEventRecorder.Kind.SOURCE_RE_RESOLVED, currentSource, -1, -1,
                    "source re-resolved after player recreation");
        }
    }

    private void recordFailure(String source, Exception error) {
        recorder.record(RuntimeEventRecorder.Kind.FAILURE, source, -1, -1,
                error.getClass().getSimpleName() + ":" + error.getMessage());
        setStatus("failure: " + source);
    }

    private void setStatus(String text) {
        if (status != null) status.setText(text);
    }

    @Override
    protected void onDestroy() {
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }

    private static void writeWav(File file) throws IOException {
        int sampleRate = 8000;
        int seconds = 2;
        int sampleCount = sampleRate * seconds;
        byte[] pcm = new byte[sampleCount * 2];
        ByteBuffer samples = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < sampleCount; i++) {
            double phase = 2.0 * Math.PI * 440.0 * i / sampleRate;
            samples.putShort((short) (Math.sin(phase) * 8000));
        }
        try (FileOutputStream out = new FileOutputStream(file)) {
            writeAscii(out, "RIFF");
            writeInt(out, 36 + pcm.length);
            writeAscii(out, "WAVEfmt ");
            writeInt(out, 16);
            writeShort(out, 1);
            writeShort(out, 1);
            writeInt(out, sampleRate);
            writeInt(out, sampleRate * 2);
            writeShort(out, 2);
            writeShort(out, 16);
            writeAscii(out, "data");
            writeInt(out, pcm.length);
            out.write(pcm);
        }
    }

    private static void writeAscii(FileOutputStream out, String value) throws IOException {
        out.write(value.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    private static void writeInt(FileOutputStream out, int value) throws IOException {
        out.write(value & 0xff);
        out.write((value >> 8) & 0xff);
        out.write((value >> 16) & 0xff);
        out.write((value >> 24) & 0xff);
    }

    private static void writeShort(FileOutputStream out, int value) throws IOException {
        out.write(value & 0xff);
        out.write((value >> 8) & 0xff);
    }
}

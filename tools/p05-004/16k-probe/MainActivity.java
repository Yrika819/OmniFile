package dev.p05.archive.poc;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import com.github.luben.zstd.Zstd;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class MainActivity extends Activity {
    private static final String TAG = "P05ZstdPoc";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        new Thread(new Runnable() {
            @Override public void run() { runAll(); }
        }).start();
    }

    private byte[] readRaw(String name) throws Exception {
        int id = getResources().getIdentifier(name, "raw", getPackageName());
        InputStream in = getResources().openRawResource(id);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        in.close();
        return bos.toByteArray();
    }

    private void runAll() {
        try {
            long ps = android.system.Os.sysconf(android.system.OsConstants._SC_PAGESIZE);
            String abi = android.os.Build.SUPPORTED_ABIS.length > 0
                    ? android.os.Build.SUPPORTED_ABIS[0] : "unknown";
            Log.i(TAG, "P05_RESULT test=identity;page_size=" + ps + ";abi=" + abi);
        } catch (Exception e) {
            Log.i(TAG, "P05_RESULT test=identity;FAIL;error=" + e);
        }
        // valid decode
        try {
            byte[] src = readRaw("valid_zstd");
            long max = Zstd.decompressedSize(src);
            byte[] dst = new byte[(int) Math.min(Math.max(max, 64), 1 << 20)];
            long out = Zstd.decompress(dst, src);
            Log.i(TAG, "P05_RESULT test=valid_zstd;PASS;out=" + out);
        } catch (Throwable t) {
            Log.i(TAG, "P05_RESULT test=valid_zstd;FAIL;error=" + t);
        }
        // tar.zst
        try {
            byte[] src = readRaw("tar_zst");
            byte[] dst = new byte[1 << 20];
            long out = Zstd.decompress(dst, src);
            Log.i(TAG, "P05_RESULT test=tar_zst;PASS;out=" + out);
        } catch (Throwable t) {
            Log.i(TAG, "P05_RESULT test=tar_zst;FAIL;error=" + t);
        }
        // repeated
        try {
            byte[] src = readRaw("valid_zstd");
            int ok = 0;
            for (int i = 0; i < 20; i++) {
                byte[] dst = new byte[1 << 16];
                Zstd.decompress(dst, src);
                ok++;
            }
            Log.i(TAG, "P05_RESULT test=repeated_load_use;PASS;count=" + ok);
        } catch (Throwable t) {
            Log.i(TAG, "P05_RESULT test=repeated_load_use;FAIL;error=" + t);
        }
        // larger (reuse tar_zst as representative larger)
        try {
            byte[] src = readRaw("tar_zst");
            byte[] dst = new byte[1 << 20];
            long out = Zstd.decompress(dst, src);
            Log.i(TAG, "P05_RESULT test=larger_decode;PASS;out=" + out);
        } catch (Throwable t) {
            Log.i(TAG, "P05_RESULT test=larger_decode;FAIL;error=" + t);
        }
        // truncated -> expect exception
        try {
            byte[] src = readRaw("truncated_zstd");
            byte[] dst = new byte[1 << 16];
            long out = Zstd.decompress(dst, src);
            Log.i(TAG, "P05_RESULT test=truncated_zstd;UNEXPECTED_PASS;out=" + out);
        } catch (Throwable t) {
            Log.i(TAG, "P05_RESULT test=truncated_zstd;CONTROLLED_ERROR;error=" + t.getClass().getName() + ":" + t.getMessage());
        }
        // malformed -> expect exception
        try {
            byte[] src = readRaw("malformed_zstd");
            byte[] dst = new byte[1 << 16];
            long out = Zstd.decompress(dst, src);
            Log.i(TAG, "P05_RESULT test=malformed_zstd;UNEXPECTED_PASS;out=" + out);
        } catch (Throwable t) {
            Log.i(TAG, "P05_RESULT test=malformed_zstd;CONTROLLED_ERROR;error=" + t.getClass().getName() + ":" + t.getMessage());
        }
        Log.i(TAG, "P05_RESULT test=suite_done;DONE");
    }
}

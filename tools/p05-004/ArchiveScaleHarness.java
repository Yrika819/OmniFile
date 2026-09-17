import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

/**
 * Bounded synthetic entry-index measurement. It is not an archive parser or UI
 * performance result; it only checks deterministic 10k/100k metadata handling.
 */
public final class ArchiveScaleHarness {
    private static final int MAX_ENTRIES = 100_000;

    public static void main(String[] args) {
        if (args.length == 0) {
            args = new String[] {"10000", "100000"};
        }
        System.out.println("SCALE_SCOPE=SYNTHETIC_ENTRY_INDEX_ONLY");
        for (String argument : args) {
            int count = Integer.parseInt(argument);
            if (count <= 0 || count > MAX_ENTRIES) {
                fail("entry count outside bounded range: " + count);
            }
            measure(count);
        }
    }

    private static void measure(int count) {
        long before = usedHeap();
        long start = System.nanoTime();
        ArrayList<String> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            entries.add(String.format(Locale.ROOT, "entry-%06d", index));
        }
        if (entries.size() != count
                || !entries.get(0).equals("entry-000000")
                || !entries.get(count / 2).equals(String.format(Locale.ROOT, "entry-%06d", count / 2))
                || !entries.get(count - 1).equals(String.format(Locale.ROOT, "entry-%06d", count - 1))) {
            fail("deterministic entry index check failed at " + count);
        }
        ArrayList<String> sorted = new ArrayList<>(entries);
        Collections.sort(sorted);
        if (!sorted.equals(entries)) {
            fail("deterministic sort check failed at " + count);
        }
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
        long heapDelta = Math.max(0, usedHeap() - before);
        System.out.println("SCALE_" + count + "=PASS elapsed_ms=" + elapsedMillis + " heap_delta_bytes=" + heapDelta);
    }

    private static long usedHeap() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static void fail(String message) {
        System.err.println("ERROR: " + message);
        System.exit(2);
    }
}

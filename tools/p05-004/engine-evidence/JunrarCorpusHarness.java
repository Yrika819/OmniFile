import com.github.junrar.Archive;
import com.github.junrar.rarfile.FileHeader;

import java.io.OutputStream;
import java.nio.file.Path;

/** Disposable Junrar corpus probe. This is not production code. */
public final class JunrarCorpusHarness {
    public static void main(String[] args) {
        for (String arg : args) {
            Path path = Path.of(arg);
            probe(path, null);
            probe(path, "junrar");
            probe(path, "wrong");
        }
    }

    private static void probe(Path path, String password) {
        String label = path.getFileName() + " password=" + (password == null ? "<none>" : password);
        try (Archive archive = password == null ? new Archive(path.toFile()) : new Archive(path.toFile(), password)) {
            int entries = 0;
            int extracted = 0;
            for (FileHeader header : archive) {
                entries++;
                if (!header.isDirectory()) {
                    archive.extractFile(header, OutputStream.nullOutputStream());
                    extracted++;
                }
            }
            System.out.println("JUNRAR_CASE=" + label + " ACCEPTED entries=" + entries
                    + " extracted=" + extracted + " encrypted=" + archive.isEncrypted()
                    + " broken=" + archive.hasBrokenHeaders());
        } catch (Throwable error) {
            System.out.println("JUNRAR_CASE=" + label + " REJECTED "
                    + error.getClass().getName() + ":" + error.getMessage());
        }
    }
}

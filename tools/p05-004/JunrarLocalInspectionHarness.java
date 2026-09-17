import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Conservative local Junrar provenance probe. It reports what is physically
 * present under the supplied root and never treats research prose as a source
 * checkout, artifact, or legal approval.
 */
public final class JunrarLocalInspectionHarness {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            fail("usage: JunrarLocalInspectionHarness <root>");
        }
        Path root = Path.of(args[0]).toAbsolutePath().normalize();
        List<Path> candidates = findCandidateRoots(root);
        boolean repository = candidates.stream().anyMatch(JunrarLocalInspectionHarness::looksLikeRepository);
        boolean license = candidates.stream().anyMatch(JunrarLocalInspectionHarness::looksLikeLicense);
        boolean source = candidates.stream().anyMatch(JunrarLocalInspectionHarness::looksLikeSource);
        System.out.println("JUNRAR_ROOT=" + root);
        System.out.println("JUNRAR_LOCAL_REPOSITORY=" + (repository ? "PRESENT" : "ABSENT"));
        System.out.println("JUNRAR_LOCAL_LICENSE=" + (license ? "PRESENT" : "ABSENT"));
        System.out.println("JUNRAR_LOCAL_SOURCE=" + (source ? "PRESENT" : "ABSENT"));
        System.out.println("JUNRAR_RAR_CREATION=UNSUPPORTED");
        System.out.println("JUNRAR_CLASSIFICATION=LICENSE_REVIEW_REQUIRED");
    }

    private static List<Path> findCandidateRoots(Path root) throws IOException {
        List<Path> candidates = new ArrayList<>();
        addIfJunrarNamed(candidates, root);
        for (String parent : List.of("vendor", "third_party", "libs", "tools")) {
            Path directory = root.resolve(parent);
            if (!Files.isDirectory(directory)) {
                continue;
            }
            try (var children = Files.list(directory)) {
                children.filter(Files::isDirectory).forEach(path -> addIfJunrarNamed(candidates, path));
            }
        }
        return candidates;
    }

    private static void addIfJunrarNamed(List<Path> candidates, Path path) {
        if (Files.exists(path) && path.getFileName() != null
                && path.getFileName().toString().toLowerCase(Locale.ROOT).contains("junrar")) {
            candidates.add(path);
        }
    }

    private static boolean looksLikeRepository(Path path) {
        return Files.isDirectory(path) && (Files.isDirectory(path.resolve(".git")) || Files.isRegularFile(path.resolve(".git")));
    }

    private static boolean looksLikeLicense(Path path) {
        return Files.isRegularFile(path.resolve("LICENSE"))
                || Files.isRegularFile(path.resolve("LICENSE.txt"))
                || Files.isRegularFile(path.resolve("LICENSE.md"));
    }

    private static boolean looksLikeSource(Path path) {
        Path sourceRoot = path.resolve("src");
        if (!Files.isDirectory(sourceRoot)) {
            return false;
        }
        try (var paths = Files.walk(sourceRoot, 8)) {
            return paths.anyMatch(candidate -> Files.isRegularFile(candidate)
                    && candidate.getFileName().toString().endsWith(".java"));
        } catch (IOException exception) {
            return false;
        }
    }

    private static void fail(String message) {
        System.err.println("ERROR: " + message);
        System.exit(2);
    }
}

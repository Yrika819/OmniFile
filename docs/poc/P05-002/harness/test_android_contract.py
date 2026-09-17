"""POC-ONLY — NOT PRODUCTION AUTHORITY.

Host-side contract checks for the disposable raw-SDK Android SAF harness.
These tests inspect source shape only; they never install or run an APK.
"""

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
ANDROID = ROOT / "android"
MANIFEST = ANDROID / "AndroidManifest.xml"
SOURCE = ANDROID / "src/dev/poc/safoperations/MainActivity.java"
BUILD = ANDROID / "build.sh"
README = ANDROID / "README.md"


class AndroidSafHarnessContractTests(unittest.TestCase):
    def test_required_disposable_artifacts_exist(self) -> None:
        for path in (MANIFEST, SOURCE, BUILD, README):
            self.assertTrue(path.is_file(), path)

    def test_package_is_isolated_and_manifest_is_launcher_only(self) -> None:
        manifest = MANIFEST.read_text(encoding="utf-8")
        self.assertIn('package="dev.poc.safoperations"', manifest)
        self.assertIn('android:minSdkVersion="31"', manifest)
        self.assertIn('android.intent.action.MAIN', manifest)
        self.assertNotIn("com.omnifile", manifest)

    def test_activity_uses_real_persistable_tree_selection(self) -> None:
        source = SOURCE.read_text(encoding="utf-8")
        for required in (
            "ACTION_OPEN_DOCUMENT_TREE",
            "FLAG_GRANT_PERSISTABLE_URI_PERMISSION",
            "takePersistableUriPermission",
            "getPersistedUriPermissions",
            "DocumentsContract",
        ):
            self.assertIn(required, source)

    def test_activity_records_directions_faults_and_durable_identity(self) -> None:
        source = SOURCE.read_text(encoding="utf-8")
        for required in (
            "LOCAL_TO_SAF",
            "SAF_TO_LOCAL",
            "SAF_TO_SAF",
            "INTERRUPTED",
            "CANCELLED",
            "MUTATION",
            "CONFLICT_DESTINATION",
            "RECONCILE",
            "FINALIZE",
            "sourceUri",
            "destinationUri",
            "providerId",
            "sourceSha256",
            "checkpointBytes",
            "deleteSource",
        ):
            self.assertIn(required, source)

    def test_build_and_run_docs_are_raw_sdk_and_non_device_automated(self) -> None:
        build = BUILD.read_text(encoding="utf-8")
        readme = README.read_text(encoding="utf-8")
        for required in ("javac", "aapt2", "d8", "apksigner"):
            self.assertIn(required, build)
        for required in (
            "./build.sh",
            "ACTION_OPEN_DOCUMENT_TREE",
            "JSONL",
            "Parent-only",
            "POC-ONLY — NOT PRODUCTION AUTHORITY",
        ):
            self.assertIn(required, readme)
        self.assertNotIn("adb", build.lower())
        self.assertNotIn("adb", readme.lower())

    def test_android_harness_contains_no_production_package_or_device_automation(self) -> None:
        for path in ANDROID.rglob("*"):
            if path.is_file() and path.suffix in {".xml", ".java", ".sh", ".md"}:
                content = path.read_text(encoding="utf-8")
                self.assertNotIn("com.omnifile", content, path)
                self.assertNotIn("adb", content.lower(), path)


if __name__ == "__main__":
    unittest.main()

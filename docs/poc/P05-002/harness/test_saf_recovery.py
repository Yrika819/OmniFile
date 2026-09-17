"""POC-ONLY — NOT PRODUCTION AUTHORITY.

Host-only tests for the P05-002 SAF-shaped recovery policy.
"""

import hashlib
import unittest

from saf_recovery import (
    InMemorySafProvider,
    OperationRecord,
    ProviderSnapshot,
    RecoveryClass,
    reconcile,
)


def record_for(source: bytes = b"0123456789abcdef", kind: str = "copy") -> OperationRecord:
    return OperationRecord(
        operation_kind=kind,
        source_uri="content://p05/source-1",
        source_version="v1",
        partial_uri="content://p05/partial-1",
        final_uri="content://p05/final-1",
        expected_sha256=hashlib.sha256(source).hexdigest(),
        expected_length=len(source),
        checkpoint_bytes=4,
        phase="TRANSFER",
    )


class SafRecoveryTests(unittest.TestCase):
    """Each test covers one deterministic recovery policy branch."""

    def test_valid_partial_uses_observed_length_over_stale_checkpoint(self) -> None:
        source = b"0123456789abcdef"
        decision = reconcile(
            record_for(source),
            ProviderSnapshot(
                provider_available=True,
                grant_available=True,
                source_available=True,
                source_version="v1",
                source_bytes=source,
                partial_bytes=source[:8],
                final_bytes=None,
            ),
        )

        self.assertEqual(decision.classification, RecoveryClass.RESUME_FROM_PARTIAL)
        self.assertEqual(decision.resume_offset, 8)

    def test_provider_disconnect_blocks_without_replay(self) -> None:
        decision = reconcile(
            record_for(),
            ProviderSnapshot(
                provider_available=False,
                grant_available=True,
                source_available=False,
                source_version=None,
                source_bytes=None,
                partial_bytes=b"0123",
                final_bytes=None,
            ),
        )

        self.assertEqual(decision.classification, RecoveryClass.BLOCKED_PROVIDER)
        self.assertIsNone(decision.resume_offset)

    def test_revoked_grant_blocks_without_automatic_retry(self) -> None:
        decision = reconcile(
            record_for(),
            ProviderSnapshot(
                provider_available=True,
                grant_available=False,
                source_available=False,
                source_version=None,
                source_bytes=None,
                partial_bytes=b"0123",
                final_bytes=None,
            ),
        )

        self.assertEqual(decision.classification, RecoveryClass.BLOCKED_PERMISSION)
        self.assertEqual(decision.action, "WAIT_FOR_AUTHORIZATION")

    def test_source_version_change_blocks_resume(self) -> None:
        source = b"0123456789abcdef"
        decision = reconcile(
            record_for(source),
            ProviderSnapshot(
                provider_available=True,
                grant_available=True,
                source_available=True,
                source_version="v2",
                source_bytes=b"changed-source!!",
                partial_bytes=source[:8],
                final_bytes=None,
            ),
        )

        self.assertEqual(decision.classification, RecoveryClass.CONFLICT_SOURCE_CHANGED)
        self.assertIsNone(decision.resume_offset)

    def test_mismatched_partial_requires_restart(self) -> None:
        source = b"0123456789abcdef"
        decision = reconcile(
            record_for(source),
            ProviderSnapshot(
                provider_available=True,
                grant_available=True,
                source_available=True,
                source_version="v1",
                source_bytes=source,
                partial_bytes=b"0123X789",
                final_bytes=None,
            ),
        )

        self.assertEqual(decision.classification, RecoveryClass.RESTART_REQUIRED)
        self.assertEqual(decision.resume_offset, 0)

    def test_valid_final_destination_does_not_delete_move_source(self) -> None:
        source = b"0123456789abcdef"
        decision = reconcile(
            record_for(source, kind="move"),
            ProviderSnapshot(
                provider_available=True,
                grant_available=True,
                source_available=True,
                source_version="v1",
                source_bytes=source,
                partial_bytes=None,
                final_bytes=source,
            ),
        )

        self.assertEqual(decision.classification, RecoveryClass.FINAL_DESTINATION_OBSERVED)
        self.assertEqual(decision.action, "REQUIRE_EXPLICIT_SOURCE_DELETE")
        self.assertFalse(decision.delete_source)

    def test_valid_final_destination_completes_copy_without_source_delete(self) -> None:
        source = b"0123456789abcdef"
        decision = reconcile(
            record_for(source, kind="copy"),
            ProviderSnapshot(
                provider_available=True,
                grant_available=True,
                source_available=True,
                source_version="v1",
                source_bytes=source,
                partial_bytes=None,
                final_bytes=source,
            ),
        )

        self.assertEqual(decision.classification, RecoveryClass.FINAL_DESTINATION_OBSERVED)
        self.assertEqual(decision.action, "MARK_COMPLETE")
        self.assertFalse(decision.delete_source)

    def test_wrong_final_destination_is_not_completion(self) -> None:
        source = b"0123456789abcdef"
        decision = reconcile(
            record_for(source),
            ProviderSnapshot(
                provider_available=True,
                grant_available=True,
                source_available=True,
                source_version="v1",
                source_bytes=source,
                partial_bytes=None,
                final_bytes=b"wrong",
            ),
        )

        self.assertEqual(decision.classification, RecoveryClass.RESTART_REQUIRED)
        self.assertEqual(decision.action, "RECREATE_PARTIAL")

    def test_missing_outputs_restart_from_zero(self) -> None:
        decision = reconcile(
            record_for(),
            ProviderSnapshot(
                provider_available=True,
                grant_available=True,
                source_available=True,
                source_version="v1",
                source_bytes=b"0123456789abcdef",
                partial_bytes=None,
                final_bytes=None,
            ),
        )

        self.assertEqual(decision.classification, RecoveryClass.RESTART_REQUIRED)
        self.assertEqual(decision.resume_offset, 0)

    def test_provider_model_keeps_locators_opaque_and_snapshot_read_only(self) -> None:
        provider = InMemorySafProvider()
        source_uri = provider.create_document(b"payload", version="v1")
        snapshot = provider.snapshot(source_uri, "content://p05/partial", "content://p05/final")

        self.assertTrue(source_uri.startswith("content://p05/"))
        self.assertIsInstance(snapshot.source_bytes, bytes)
        self.assertNotIn("/", source_uri.removeprefix("content://p05/"))


if __name__ == "__main__":
    unittest.main()

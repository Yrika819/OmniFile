"""POC-ONLY — NOT PRODUCTION AUTHORITY.

Host-side SAF-shaped recovery policy. It never calls Android APIs or writes a
host filesystem path.
"""

from dataclasses import dataclass
from enum import Enum
import hashlib
from typing import Dict, Optional


class RecoveryClass(str, Enum):
    INVALID_RECORD = "INVALID_RECORD"
    BLOCKED_PROVIDER = "BLOCKED_PROVIDER"
    BLOCKED_PERMISSION = "BLOCKED_PERMISSION"
    CONFLICT_SOURCE_CHANGED = "CONFLICT_SOURCE_CHANGED"
    CONFLICT_DESTINATION = "CONFLICT_DESTINATION"
    FINALIZATION_AMBIGUOUS = "FINALIZATION_AMBIGUOUS"
    FINAL_DESTINATION_OBSERVED = "FINAL_DESTINATION_OBSERVED"
    RESUME_FROM_PARTIAL = "RESUME_FROM_PARTIAL"
    RESTART_REQUIRED = "RESTART_REQUIRED"


@dataclass(frozen=True)
class OperationRecord:
    operation_kind: str
    source_uri: str
    source_version: str
    partial_uri: str
    final_uri: str
    expected_sha256: str
    expected_length: int
    checkpoint_bytes: int
    phase: str
    finalization_acknowledged: bool = False
    source_provider_id: str = "source"
    destination_provider_id: str = "destination"


@dataclass(frozen=True)
class ProviderSnapshot:
    provider_available: bool
    grant_available: bool
    source_available: bool
    source_version: Optional[str]
    source_bytes: Optional[bytes]
    partial_bytes: Optional[bytes]
    final_bytes: Optional[bytes]
    source_provider_available: Optional[bool] = None
    destination_provider_available: Optional[bool] = None
    source_grant_available: Optional[bool] = None
    destination_grant_available: Optional[bool] = None
    source_provider_id: str = "source"
    destination_provider_id: str = "destination"


@dataclass(frozen=True)
class RecoveryDecision:
    classification: RecoveryClass
    action: str
    resume_offset: Optional[int] = None
    reason: str = ""
    delete_source: bool = False


class InMemorySafProvider:
    """A deterministic provider-shaped fixture with opaque locators."""

    def __init__(self) -> None:
        self._documents: Dict[str, bytes] = {}
        self._versions: Dict[str, str] = {}
        self._next_id = 1
        self.provider_available = True
        self.grant_available = True

    def create_document(self, content: bytes, version: str = "v1") -> str:
        uri = "content://p05/{}".format(self._next_id)
        self._next_id += 1
        self._documents[uri] = bytes(content)
        self._versions[uri] = version
        return uri

    def replace_document(self, uri: str, content: bytes, version: str = "v1") -> None:
        self._documents[uri] = bytes(content)
        self._versions[uri] = version

    def revoke_grant(self) -> None:
        self.grant_available = False

    def disconnect(self) -> None:
        self.provider_available = False

    def snapshot(self, source_uri: str, partial_uri: str, final_uri: str) -> ProviderSnapshot:
        source_visible = self.provider_available and self.grant_available and source_uri in self._documents
        return ProviderSnapshot(
            provider_available=self.provider_available,
            grant_available=self.grant_available,
            source_available=source_visible,
            source_version=self._versions.get(source_uri) if source_visible else None,
            source_bytes=self._documents.get(source_uri) if source_visible else None,
            partial_bytes=self._documents.get(partial_uri) if self.provider_available else None,
            final_bytes=self._documents.get(final_uri) if self.provider_available else None,
        )


def _sha256(content: Optional[bytes]) -> Optional[str]:
    if content is None:
        return None
    return hashlib.sha256(content).hexdigest()


def reconcile(record: OperationRecord, snapshot: ProviderSnapshot) -> RecoveryDecision:
    """Classify restart state without mutating provider or source state."""

    valid_phases = {"PLAN", "CREATE_PARTIAL", "TRANSFER", "CHECKPOINT", "VERIFY", "FINALIZE", "COMPLETE"}
    if (
        record.operation_kind not in {"copy", "move"}
        or record.phase not in valid_phases
        or record.expected_length < 0
        or record.checkpoint_bytes < 0
        or record.checkpoint_bytes > record.expected_length
        or len(record.expected_sha256) != 64
        or any(c not in "0123456789abcdef" for c in record.expected_sha256)
        or (record.phase == "COMPLETE" and not record.finalization_acknowledged)
    ):
        return RecoveryDecision(
            RecoveryClass.INVALID_RECORD,
            "BLOCK_MALFORMED_OPERATION",
            reason="durable operation record is inconsistent or terminal without finalization acknowledgement",
        )

    source_provider_available = (
        snapshot.source_provider_available
        if snapshot.source_provider_available is not None
        else snapshot.provider_available
    )
    destination_provider_available = (
        snapshot.destination_provider_available
        if snapshot.destination_provider_available is not None
        else snapshot.provider_available
    )
    source_grant_available = (
        snapshot.source_grant_available
        if snapshot.source_grant_available is not None
        else snapshot.grant_available
    )
    destination_grant_available = (
        snapshot.destination_grant_available
        if snapshot.destination_grant_available is not None
        else snapshot.grant_available
    )

    if (
        not destination_provider_available
        or snapshot.destination_provider_id != record.destination_provider_id
    ):
        return RecoveryDecision(
            RecoveryClass.BLOCKED_PROVIDER,
            "WAIT_FOR_PROVIDER",
            reason="destination provider authority is unavailable or rebound",
        )

    if not destination_grant_available:
        return RecoveryDecision(
            RecoveryClass.BLOCKED_PERMISSION,
            "WAIT_FOR_AUTHORIZATION",
            reason="destination access or persisted grant is unavailable",
        )

    final_present = snapshot.final_bytes is not None
    final_matches = (
        final_present
        and len(snapshot.final_bytes) == record.expected_length
        and _sha256(snapshot.final_bytes) == record.expected_sha256
    )
    if final_present and not final_matches:
        return RecoveryDecision(
            RecoveryClass.CONFLICT_DESTINATION,
            "REQUIRE_USER_REVIEW",
            reason="final destination exists but does not match the expected content",
        )
    if final_matches:
        if not record.finalization_acknowledged:
            return RecoveryDecision(
                RecoveryClass.FINALIZATION_AMBIGUOUS,
                "REQUIRE_USER_REVIEW",
                reason="matching final bytes exist without a durable finalization acknowledgement",
            )
        if record.operation_kind == "move" and (
            not source_provider_available
            or not source_grant_available
            or not snapshot.source_available
        ):
            return RecoveryDecision(
                RecoveryClass.FINAL_DESTINATION_OBSERVED,
                "WAIT_FOR_SOURCE_AUTHORIZATION",
                reason="destination is verified but source authority is unavailable for the explicit move-delete step",
            )
        if record.operation_kind == "move" and (
            snapshot.source_provider_id != record.source_provider_id
            or snapshot.source_version != record.source_version
            or snapshot.source_bytes is None
            or len(snapshot.source_bytes) != record.expected_length
            or _sha256(snapshot.source_bytes) != record.expected_sha256
        ):
            return RecoveryDecision(
                RecoveryClass.CONFLICT_SOURCE_CHANGED,
                "REQUIRE_USER_REVIEW",
                reason="source identity, version, length, or content changed before move deletion authorization",
            )
        action = (
            "REQUIRE_EXPLICIT_SOURCE_DELETE"
            if record.operation_kind == "move"
            else "MARK_COMPLETE"
        )
        return RecoveryDecision(
            RecoveryClass.FINAL_DESTINATION_OBSERVED,
            action,
            reason="final destination is verified and finalization was durably acknowledged",
        )

    if (
        snapshot.source_provider_id != record.source_provider_id
        or not source_provider_available
        or not source_grant_available
        or not snapshot.source_available
        or snapshot.source_bytes is None
    ):
        return RecoveryDecision(
            RecoveryClass.BLOCKED_PERMISSION,
            "WAIT_FOR_AUTHORIZATION",
            reason="source access or persisted grant is unavailable",
        )

    if snapshot.source_version != record.source_version or _sha256(snapshot.source_bytes) != record.expected_sha256 or len(snapshot.source_bytes) != record.expected_length:
        return RecoveryDecision(
            RecoveryClass.CONFLICT_SOURCE_CHANGED,
            "REQUIRE_USER_REVIEW",
            reason="source version or expected source content changed",
        )

    partial = snapshot.partial_bytes
    source = snapshot.source_bytes
    if partial is not None and source is not None and source.startswith(partial):
        action = "VERIFY_AND_FINALIZE" if len(partial) == record.expected_length else "TRANSFER_FROM_OFFSET"
        return RecoveryDecision(
            RecoveryClass.RESUME_FROM_PARTIAL,
            action,
            resume_offset=len(partial),
            reason="observed partial prefix is authoritative over stale checkpoint metadata",
        )

    return RecoveryDecision(
        RecoveryClass.RESTART_REQUIRED,
        "RECREATE_PARTIAL",
        resume_offset=0,
        reason=(
            "partial destination is not a valid source prefix"
            if partial is not None
            else "no usable partial or finalized destination was observed"
        ),
    )

"""Disposable P05-003 provider/source contract model.

This is host-side evidence code only. It deliberately has no AndroidX, Media3,
filesystem, network, or device integration and must not be treated as a player.
"""

from dataclasses import dataclass
from enum import Enum
import re


class SourceMode(str, Enum):
    DIRECT_DESCRIPTOR = "direct_descriptor"
    SEEKABLE_SOURCE = "seekable_source"
    RANDOM_RANGE_SOURCE = "random_range_source"
    SEQUENTIAL_CACHED_SOURCE = "sequential_cached_source"
    UNSUPPORTED = "unsupported"


class EventKind(str, Enum):
    SOURCE_RESOLVED = "source_resolved"
    PREPARE = "prepare"
    START = "start"
    PLAYBACK = "playback"
    DURATION = "duration"
    SEEK = "seek"
    EOF = "eof"
    STOP = "stop"
    REOPEN = "reopen"
    PLAYER_RECREATED = "player_recreated"
    FAILURE = "failure"


@dataclass(frozen=True)
class RuntimeEvent:
    sequence: int
    kind: EventKind
    source: str = ""
    position_ms: int = -1
    duration_ms: int = -1
    detail: str = ""


class EventRecorder:
    """Deterministic, redacted event log contract for the runtime PoC."""

    _secret_query = re.compile(
        r"([?&](?:token|access_token|sig|signature|authorization)=)[^&\s]+",
        re.IGNORECASE,
    )

    def __init__(self):
        self._events = []

    def record(
        self,
        kind: EventKind,
        source: str = "",
        position_ms: int = -1,
        duration_ms: int = -1,
        detail: str = "",
    ) -> None:
        safe_source = self._secret_query.sub(r"\1<redacted>", source)
        self._events.append(
            RuntimeEvent(
                sequence=len(self._events),
                kind=kind,
                source=safe_source,
                position_ms=position_ms,
                duration_ms=duration_ms,
                detail=self._secret_query.sub(r"\1<redacted>", detail),
            )
        )

    def events(self):
        return tuple(self._events)

    def kinds(self):
        return [event.kind for event in self._events]


class UnsupportedSeek(Exception):
    """Raised when a sequential source is asked to seek."""


class SequentialReadSource:
    """Finite, repeatable sequential source with deliberately no seek path."""

    def __init__(self, content: bytes):
        self._content = bytes(content)
        self._position = 0

    def read(self, length: int) -> bytes:
        if length <= 0 or self._position >= len(self._content):
            return b""
        end = min(self._position + length, len(self._content))
        result = self._content[self._position:end]
        self._position = end
        return result

    def seek(self, position: int) -> None:
        del position
        raise UnsupportedSeek("sequential source does not support seeking")

    def reopen(self) -> None:
        self._position = 0


@dataclass(frozen=True)
class ReadCapabilities:
    sequential: bool = False
    seekable: bool = False
    random_range: bool = False
    native_descriptor: bool = False
    descriptor_readable: bool = False


@dataclass(frozen=True)
class MediaIdentity:
    provider_id: str
    object_id: str
    source_version: str
    locator: str


@dataclass(frozen=True)
class PlaybackRequest:
    requires_seek: bool
    prefers_random_range: bool = False


@dataclass(frozen=True)
class Resolution:
    mode: SourceMode
    cache_required: bool = False
    reason: str = ""


def resolve_source(
    identity: MediaIdentity,
    capabilities: ReadCapabilities,
    request: PlaybackRequest,
) -> Resolution:
    """Choose the weakest advertised source mode that satisfies the request."""

    # Keep identity in the signature to make the boundary explicit. The
    # locator is not inspected: resolution must use capabilities, not paths.
    del identity

    if request.requires_seek:
        if capabilities.random_range and request.prefers_random_range:
            return Resolution(SourceMode.RANDOM_RANGE_SOURCE)
        if (
            capabilities.native_descriptor
            and capabilities.descriptor_readable
            and capabilities.seekable
        ):
            return Resolution(SourceMode.DIRECT_DESCRIPTOR)
        if capabilities.seekable:
            return Resolution(SourceMode.SEEKABLE_SOURCE)
        if capabilities.random_range:
            return Resolution(SourceMode.RANDOM_RANGE_SOURCE)
        return Resolution(
            SourceMode.UNSUPPORTED,
            reason="seek requires seekable, random-range, or seekable native-descriptor access",
        )

    if capabilities.sequential:
        return Resolution(SourceMode.SEQUENTIAL_CACHED_SOURCE, cache_required=True)
    if capabilities.native_descriptor and capabilities.descriptor_readable:
        return Resolution(SourceMode.DIRECT_DESCRIPTOR)
    if capabilities.seekable:
        return Resolution(SourceMode.SEEKABLE_SOURCE)
    if capabilities.random_range:
        return Resolution(SourceMode.RANDOM_RANGE_SOURCE)
    return Resolution(SourceMode.UNSUPPORTED, reason="no readable capability advertised")


def cache_key(identity: MediaIdentity) -> str:
    """Build a durable cache identity without using the transport locator."""

    fields = (identity.provider_id, identity.object_id, identity.source_version)
    encoded = "|".join(f"{len(field)}:{field}" for field in fields)
    return f"p05-003|{encoded}"

"""Disposable P05-003 provider/source contract model.

This is host-side evidence code only. It deliberately has no AndroidX, Media3,
filesystem, network, or device integration and must not be treated as a player.
"""

from dataclasses import dataclass
from enum import Enum


class SourceMode(str, Enum):
    DIRECT_DESCRIPTOR = "direct_descriptor"
    SEEKABLE_SOURCE = "seekable_source"
    RANDOM_RANGE_SOURCE = "random_range_source"
    SEQUENTIAL_CACHED_SOURCE = "sequential_cached_source"
    UNSUPPORTED = "unsupported"


@dataclass(frozen=True)
class ReadCapabilities:
    sequential: bool = False
    seekable: bool = False
    random_range: bool = False
    native_descriptor: bool = False


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
        if capabilities.native_descriptor and capabilities.seekable:
            return Resolution(SourceMode.DIRECT_DESCRIPTOR)
        if capabilities.random_range and request.prefers_random_range:
            return Resolution(SourceMode.RANDOM_RANGE_SOURCE)
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
    if capabilities.native_descriptor:
        return Resolution(SourceMode.DIRECT_DESCRIPTOR)
    if capabilities.seekable:
        return Resolution(SourceMode.SEEKABLE_SOURCE)
    if capabilities.random_range:
        return Resolution(SourceMode.RANDOM_RANGE_SOURCE)
    return Resolution(SourceMode.UNSUPPORTED, reason="no readable capability advertised")


def cache_key(identity: MediaIdentity) -> str:
    """Build a durable cache identity without using the transport locator."""

    return "p05-003|{}|{}|{}".format(
        identity.provider_id,
        identity.object_id,
        identity.source_version,
    )

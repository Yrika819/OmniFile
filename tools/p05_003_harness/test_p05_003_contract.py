import unittest

from p05_003_contract import (
    EventKind,
    EventRecorder,
    MediaIdentity,
    PlaybackRequest,
    ReadCapabilities,
    SequentialReadSource,
    SourceMode,
    UnsupportedSeek,
    cache_key,
    resolve_source,
)


class ProviderNeutralPlaybackContractTests(unittest.TestCase):
    def test_prefers_native_descriptor_when_it_can_satisfy_seek(self):
        result = resolve_source(
            MediaIdentity("local", "song-1", "mtime-7", "file:///private/song.flac"),
            ReadCapabilities(
                sequential=True,
                seekable=True,
                native_descriptor=True,
                descriptor_readable=True,
            ),
            PlaybackRequest(requires_seek=True),
        )

        self.assertEqual(SourceMode.DIRECT_DESCRIPTOR, result.mode)
        self.assertFalse(result.cache_required)

    def test_prefers_random_range_when_requested(self):
        result = resolve_source(
            MediaIdentity("smb", "song-1", "etag-7", "smb://host/share/song.flac"),
            ReadCapabilities(sequential=True, random_range=True),
            PlaybackRequest(requires_seek=True, prefers_random_range=True),
        )

        self.assertEqual(SourceMode.RANDOM_RANGE_SOURCE, result.mode)
        self.assertFalse(result.cache_required)

    def test_requested_random_range_wins_when_descriptor_and_range_are_both_available(self):
        result = resolve_source(
            MediaIdentity("hybrid", "song-1", "v1", "opaque://song-1"),
            ReadCapabilities(
                sequential=True,
                seekable=True,
                random_range=True,
                native_descriptor=True,
                descriptor_readable=True,
            ),
            PlaybackRequest(requires_seek=True, prefers_random_range=True),
        )

        self.assertEqual(SourceMode.RANDOM_RANGE_SOURCE, result.mode)

    def test_uses_seekable_source_when_random_range_is_not_available(self):
        result = resolve_source(
            MediaIdentity("saf", "doc-1", "generation-7", "content://provider/doc-1"),
            ReadCapabilities(sequential=True, seekable=True),
            PlaybackRequest(requires_seek=True),
        )

        self.assertEqual(SourceMode.SEEKABLE_SOURCE, result.mode)
        self.assertFalse(result.cache_required)

    def test_sequential_only_source_requires_cached_fallback_for_non_seek_playback(self):
        result = resolve_source(
            MediaIdentity("cloud", "object-1", "v7", "https://download/object?token=secret"),
            ReadCapabilities(sequential=True),
            PlaybackRequest(requires_seek=False),
        )

        self.assertEqual(SourceMode.SEQUENTIAL_CACHED_SOURCE, result.mode)
        self.assertTrue(result.cache_required)

    def test_sequential_only_source_cannot_claim_seek(self):
        result = resolve_source(
            MediaIdentity("cloud", "object-1", "v7", "https://download/object?token=secret"),
            ReadCapabilities(sequential=True),
            PlaybackRequest(requires_seek=True),
        )

        self.assertEqual(SourceMode.UNSUPPORTED, result.mode)
        self.assertIn("seek", result.reason)

    def test_empty_capabilities_are_unsupported(self):
        result = resolve_source(
            MediaIdentity("unknown", "object-1", "v7", "opaque://locator"),
            ReadCapabilities(),
            PlaybackRequest(requires_seek=False),
        )

        self.assertEqual(SourceMode.UNSUPPORTED, result.mode)

    def test_descriptor_presence_without_read_guarantee_is_not_readable(self):
        result = resolve_source(
            MediaIdentity("saf", "doc-1", "generation-7", "content://provider/doc-1"),
            ReadCapabilities(native_descriptor=True),
            PlaybackRequest(requires_seek=False),
        )

        self.assertEqual(SourceMode.UNSUPPORTED, result.mode)

    def test_cache_key_excludes_locator_and_includes_provider_object_and_version(self):
        first = MediaIdentity("drive", "object-1", "v7", "https://download/object?token=one")
        second = MediaIdentity("drive", "object-1", "v7", "https://download/object?token=two")
        changed = MediaIdentity("drive", "object-1", "v8", "https://download/object?token=three")

        self.assertEqual(cache_key(first), cache_key(second))
        self.assertNotEqual(cache_key(first), cache_key(changed))
        self.assertNotIn("token", cache_key(first))
        self.assertIn("drive", cache_key(first))
        self.assertIn("object-1", cache_key(first))
        self.assertIn("v7", cache_key(first))

    def test_cache_key_is_collision_safe_for_delimiter_containing_identity_fields(self):
        first = MediaIdentity("a|b", "c", "v1", "opaque://one")
        second = MediaIdentity("a", "b|c", "v1", "opaque://two")

        self.assertNotEqual(cache_key(first), cache_key(second))

    def test_runtime_recorder_captures_required_lifecycle_events_in_order(self):
        recorder = EventRecorder()
        recorder.record(EventKind.SOURCE_RESOLVED, source="local")
        recorder.record(EventKind.PREPARE, source="local")
        recorder.record(EventKind.START, source="local")
        recorder.record(EventKind.DURATION, source="local", duration_ms=1200)
        recorder.record(EventKind.PLAYBACK, source="local", position_ms=0)
        recorder.record(EventKind.SEEK, source="local", position_ms=600)
        recorder.record(EventKind.PLAYBACK, source="local", position_ms=600)
        recorder.record(EventKind.EOF, source="local", position_ms=1200)
        recorder.record(EventKind.STOP, source="local", position_ms=1200)
        recorder.record(EventKind.REOPEN, source="local")
        recorder.record(EventKind.PLAYER_RECREATED, source="local")
        recorder.record(EventKind.SOURCE_RESOLVED, source="local", detail="re-resolved")

        events = recorder.events()
        self.assertEqual(list(range(len(events))), [event.sequence for event in events])
        self.assertEqual(
            [
                EventKind.SOURCE_RESOLVED,
                EventKind.PREPARE,
                EventKind.START,
                EventKind.DURATION,
                EventKind.PLAYBACK,
                EventKind.SEEK,
                EventKind.PLAYBACK,
                EventKind.EOF,
                EventKind.STOP,
                EventKind.REOPEN,
                EventKind.PLAYER_RECREATED,
                EventKind.SOURCE_RESOLVED,
            ],
            recorder.kinds(),
        )
        self.assertEqual(1200, events[3].duration_ms)
        self.assertEqual(600, events[5].position_ms)

    def test_runtime_recorder_retains_failure_details_without_locator(self):
        recorder = EventRecorder()
        recorder.record(
            EventKind.FAILURE,
            source="saf",
            detail="SecurityException for content://provider/doc?token=secret",
        )

        event = recorder.events()[0]
        self.assertEqual(EventKind.FAILURE, event.kind)
        self.assertIn("SecurityException", event.detail)
        self.assertNotIn("token=secret", event.detail)

    def test_runtime_recorder_redacts_query_secrets_in_source_and_detail(self):
        recorder = EventRecorder()
        recorder.record(
            EventKind.FAILURE,
            source="https://provider/object?token=source-secret&name=song.flac",
            detail="open failed?access_token=detail-secret&retry=1",
        )

        event = recorder.events()[0]
        self.assertNotIn("source-secret", event.source)
        self.assertNotIn("detail-secret", event.detail)
        self.assertEqual(
            "https://provider/object?token=<redacted>&name=song.flac",
            event.source,
        )

    def test_sequential_source_reads_in_fixed_chunks_and_has_no_seek(self):
        source = SequentialReadSource(b"abcdef")

        self.assertEqual(b"ab", source.read(2))
        self.assertEqual(b"cde", source.read(3))
        self.assertEqual(b"f", source.read(3))
        self.assertEqual(b"", source.read(3))
        with self.assertRaises(UnsupportedSeek):
            source.seek(0)

    def test_sequential_source_reopen_starts_at_zero(self):
        source = SequentialReadSource(b"abc")
        self.assertEqual(b"a", source.read(1))

        source.reopen()

        self.assertEqual(b"abc", source.read(4))


if __name__ == "__main__":
    unittest.main()

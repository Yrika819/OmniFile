import unittest

from p05_003_contract import (
    MediaIdentity,
    PlaybackRequest,
    ReadCapabilities,
    SourceMode,
    cache_key,
    resolve_source,
)


class ProviderNeutralPlaybackContractTests(unittest.TestCase):
    def test_prefers_native_descriptor_when_it_can_satisfy_seek(self):
        result = resolve_source(
            MediaIdentity("local", "song-1", "mtime-7", "file:///private/song.flac"),
            ReadCapabilities(sequential=True, seekable=True, native_descriptor=True),
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


if __name__ == "__main__":
    unittest.main()

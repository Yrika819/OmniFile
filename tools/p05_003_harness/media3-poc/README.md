# P05-003 disposable Media3 runtime harness

This directory is a throwaway Android application for the OMNIFILE P0.5
runtime question. It is not a production app, does not use the production
package id, and does not establish a Technology Freeze. The only dependency
pin is the P05-003-local Media3 `1.3.1` pin in `app/build.gradle`.

The app uses actual AndroidX Media3/ExoPlayer and exposes:

- app-private deterministic WAV and committed primary FLAC fixtures as real
  local sources;
- `ACTION_OPEN_DOCUMENT_TREE`, followed by a real child-document query;
- direct Media3 URI playback and a custom sequential/non-seekable Media3
  `DataSource` backed by `ContentResolver.openInputStream`;
- buttons for prepare/start, duration/playback callbacks, seek, EOF, stop,
  reopen, player recreation, source re-resolution, and failure capture.

Every probe appends redacted JSONL to the app-private
`files/p05_003_runtime.jsonl` file and mirrors the same events to the
`P05-003` log tag. The parent performed an authorized Pixel 7a API 36 runtime
capture; this directory remains disposable and does not establish a Technology
Freeze.

## Exact local build

From this directory, with the Android SDK installed at the path below:

```sh
export ANDROID_HOME=/Users/yuta/Library/Android/sdk
export GRADLE_USER_HOME=/Users/yuta/.gradle
/Users/yuta/.gradle/wrapper/dists/gradle-9.6.0-bin/42k10rwplmzkhuboz9kdazi7s/gradle-9.6.0/bin/gradle :app:assembleDebug
```

The resulting APK is a disposable build artifact. Installing/running it and
human audio acceptance remain parent-controlled; the recorded runtime result
is summarized in `docs/poc/P05-003/04_RESULTS.md`.

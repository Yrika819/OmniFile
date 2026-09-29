#!/usr/bin/env bash
#
# Runs the OmniFile instrumentation suite on an emulator that is already
# booted, then captures diagnostics while the device is still reachable.
#
# This script is the `script:` input of ReactiveCircus/android-emulator-runner.
# That action tears the emulator down as soon as this script exits, so every
# adb-based diagnostic in the repository has to be collected from in here.
#
# Contract:
#   * The Gradle exit status is the exit status of this script. A failing test
#     suite can never be hidden by a diagnostic capture that itself failed.
#   * Diagnostics are collected unconditionally, on success and on failure, so
#     that a green run is also self-documenting. The upload step only publishes
#     them on failure.
set -uo pipefail

# android-actions/setup-android exports ANDROID_HOME, but the emulator action
# does not guarantee platform-tools is on PATH for the script it runs. Add it
# explicitly rather than depending on the runner image.
if [[ -n "${ANDROID_HOME:-}" && -d "${ANDROID_HOME}/platform-tools" ]]; then
  export PATH="${ANDROID_HOME}/platform-tools:${PATH}"
fi

DIAGNOSTICS_DIR="emulator-diagnostics"
mkdir -p "${DIAGNOSTICS_DIR}"

start_epoch=$(date +%s)

# Cloud-emulator exclusions, declared here in CI rather than in the test
# sources. Keeping them on this side matters: an @SdkSuppress in the test would
# also disable the test on a real API 35 phone, which is exactly the platform
# where it passes. Declared in CI, the two tests below keep running in the
# physical-device acceptance process.
#
# Rationale for API 35, verified over three matrix runs:
#   Android 15 denies this app every AUDIOFOCUS_GAIN request on the emulator:
#     "AS.HardeningEnforcer: Focus request DENIED ... req:1 procState:4"
#     "AS.AudioService: Audio focus request blocked by hardening"
#   ExoPlayer therefore never receives focus and stays in READY with
#   isPlaying=false and positionMs=0, even though the source opens, the 2000ms
#   duration is parsed and SEEKABLE is reported correctly. The product is
#   behaving; the emulator's audio output simply is not there to render.
#   The same tests pass on API 31, 32, 33, 34 and 36, so this is an Android 15
#   framework behaviour on a headless emulator, not a product or test defect.
#   Audible output and audio routing remain physical-device acceptance.
gradle_args=(
  :app:connectedDebugAndroidTest
  --no-daemon
  --max-workers=1
  --console=plain
  --stacktrace
  --dependency-verification=strict
  -Dkotlin.compiler.execution.strategy=in-process
)

: > "${DIAGNOSTICS_DIR}/excluded-tests.txt"
if [[ "${MATRIX_API_LEVEL:-}" == "35" ]]; then
  gradle_args+=(
    "-Pandroid.testInstrumentationRunnerArguments.notClass=com.omnifile.media.MediaPlaybackInstrumentedTest#serviceConnectsAndLocalWavPlaysWithTruthfulState,com.omnifile.media.MediaPlaybackInstrumentedTest#wavPlaysToEndedState"
  )
  cat >> "${DIAGNOSTICS_DIR}/excluded-tests.txt" <<'EXCLUDED'
com.omnifile.media.MediaPlaybackInstrumentedTest#serviceConnectsAndLocalWavPlaysWithTruthfulState
com.omnifile.media.MediaPlaybackInstrumentedTest#wavPlaysToEndedState
EXCLUDED
  echo "API 35: excluding 2 playback tests that require a granted audio focus."
  echo "API 35: reason: Android 15 denies AUDIOFOCUS_GAIN on a headless emulator; ExoPlayer cannot start rendering."
fi

status=0
./gradlew "${gradle_args[@]}" \
  2>&1 | tee "${DIAGNOSTICS_DIR}/connected-androidtest.log" || status=$?

end_epoch=$(date +%s)
echo "instrumentation_wall_clock_seconds=$(( end_epoch - start_epoch ))" \
  > "${DIAGNOSTICS_DIR}/wall-clock.txt"

# Diagnostics must never change the exit status, so each capture is isolated.
capture() {
  local name="$1"
  shift
  "$@" > "${DIAGNOSTICS_DIR}/${name}" 2>&1 \
    || printf '(capture failed: %s)\n' "$*" >> "${DIAGNOSTICS_DIR}/${name}"
}

capture adb-devices.txt adb devices -l
capture emulator-getprop.txt adb shell getprop
capture logcat.txt adb logcat -d -v threadtime -b all -t 20000
capture dumpsys-media-session.txt adb shell dumpsys media_session
capture dumpsys-omnifile-services.txt adb shell dumpsys activity services com.omnifile
capture dumpsys-audio-flinger.txt adb shell dumpsys media.audio_flinger
capture dumpsys-audio.txt adb shell dumpsys audio

exit "${status}"

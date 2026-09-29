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

status=0
./gradlew :app:connectedDebugAndroidTest \
  --no-daemon \
  --max-workers=1 \
  --console=plain \
  --stacktrace \
  --dependency-verification=strict \
  -Dkotlin.compiler.execution.strategy=in-process \
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

exit "${status}"

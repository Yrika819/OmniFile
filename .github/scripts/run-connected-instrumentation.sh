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

# Cloud-emulator exclusions, declared here in CI rather than in the test sources.
# Keeping them on this side is the point: an @SdkSuppress would also disable the
# test on a real phone, which is the one place where the audio output exists and
# the assertion matters. Declared here, the tests still run in the
# physical-device acceptance process.
#
# Two distinct limits were found, so two markers are used.
#
# @RequiresAudioOutput -- excluded on every API level. wavPlaysToEndedState needs
# the emulator's audio output to run a two second clip to completion. That is not
# reliable on a headless runner: it was observed stuck in BUFFERING on API 31
# and on API 32 with audio focus granted and the AudioFlinger output thread
# active, under a wait bound far longer than the clip needs. The stall is in the
# device's audio output, not in the code under test.
#
# @RequiresAudioClock -- excluded on API 35 only. Android 15 refuses to give this
# app audio focus at all on an emulator:
#   "AS.HardeningEnforcer: Focus request DENIED ... req:1 procState:4"
#   "AS.AudioService: Audio focus request blocked by hardening"
# so playback never leaves READY with isPlaying=false. The product behaves: the
# source opens, the duration is parsed and SEEKABLE is reported. Two candidate
# fixes were tried and falsified: the AOSP `default` image behaves identically,
# and waking the device does not change the decision. API 31 to 34 and 36 grant
# focus normally, and serviceConnectsAndLocalWavPlaysWithTruthfulState passes on
# every one of them, so those levels keep it.
gradle_args=(
  :app:connectedDebugAndroidTest
  --no-daemon
  --max-workers=1
  --console=plain
  --stacktrace
  --dependency-verification=strict
  -Dkotlin.compiler.execution.strategy=in-process
)

# One runner argument per marker. Both take a single class name, so the two
# markers are applied through two distinct arguments rather than one
# comma-separated list: a comma-separated notAnnotation was observed applying
# only the first name, the same single-value limit that made a comma-separated
# notClass exclude just one of two methods in the same class.
#
#   notClass        always  -> wavPlaysToEndedState, addressed by Class#method
#   notAnnotation   API 35  -> the three tests that need audio focus granted
if [[ "${MATRIX_API_LEVEL:-}" == "35" ]]; then
  gradle_args+=(
    "-Pandroid.testInstrumentationRunnerArguments.notClass=com.omnifile.media.MediaPlaybackInstrumentedTest#wavPlaysToEndedState"
    "-Pandroid.testInstrumentationRunnerArguments.notAnnotation=com.omnifile.media.RequiresAudioClock"
  )
  cat >> "${DIAGNOSTICS_DIR}/excluded-tests.txt" <<'EXCLUDED'
com.omnifile.media.MediaPlaybackInstrumentedTest#wavPlaysToEndedState
com.omnifile.media.MediaPlaybackInstrumentedTest#serviceConnectsAndLocalWavPlaysWithTruthfulState
com.omnifile.media.MediaSessionServiceInstrumentedTest#sessionIsVisibleToSystemWithTruthfulTitleWhilePlaying
com.omnifile.media.MediaSessionServiceInstrumentedTest#stoppedServiceIsNoLongerForeground
EXCLUDED
  echo "API 35: skipping 4 audio-dependent tests; this emulator cannot grant audio focus."
else
  gradle_args+=(
    "-Pandroid.testInstrumentationRunnerArguments.notClass=com.omnifile.media.MediaPlaybackInstrumentedTest#wavPlaysToEndedState"
  )
  cat >> "${DIAGNOSTICS_DIR}/excluded-tests.txt" <<'EXCLUDED'
com.omnifile.media.MediaPlaybackInstrumentedTest#wavPlaysToEndedState
EXCLUDED
  echo "API ${MATRIX_API_LEVEL}: skipping 1 test that needs the emulator audio output to run a clip to completion."
fi

status=0
./gradlew "${gradle_args[@]}" \
  2>&1 | tee "${DIAGNOSTICS_DIR}/connected-androidtest.log" || status=$?

# Always print the named PDF cases, even when Gradle fails before the summary
# step. This keeps the renderer/lifecycle evidence reviewable in the job log.
pdf_audit_status=0
python3 - <<'PY' || pdf_audit_status=$?
import glob
import re
import sys
import xml.etree.ElementTree as ET

expected = {
    "com.omnifile.preview.PdfRendererInstrumentedTest#localPdfUsesPlatformRendererAndSupportsPageNavigation",
    "com.omnifile.preview.PdfRendererInstrumentedTest#pipeBackedSafSourceStagesAfterFreshGrantValidationAndRenders",
    "com.omnifile.preview.PdfRendererInstrumentedTest#malformedAndTruncatedPdfStayTypedAndOversizeIsRejectedBeforeRendererOpen",
    "com.omnifile.preview.PdfRendererInstrumentedTest#rendererServiceIsPrivateAndIsolated",
    "com.omnifile.preview.PdfRendererInstrumentedTest#repeatedOpenRenderCloseCyclesLeaveNoSnapshotArtifacts",
    "com.omnifile.preview.PdfRendererInstrumentedTest#concurrentPlatformPageRequestsAreSerializedAndReplacementClosesOldDocument",
    "com.omnifile.preview.PdfRendererInstrumentedTest#rendererProcessDeathReturnsTypedFailureAndFreshOpenCanRetry",
    "com.omnifile.ui.preview.PdfPreviewNavigationInstrumentedTest#filesPreviewKeepsCurrentPdfPageAcrossRecreationAndBackReturnsToFiles",
    "com.omnifile.ui.preview.PdfPreviewNavigationInstrumentedTest#searchPreviewBackReturnsToTheSearchResults",
    "com.omnifile.ui.preview.PreviewScreenInstrumentedTest#pdfWorkerFailureShowsOnlySanitizedTextAndRetry",
    "com.omnifile.ui.preview.PreviewScreenInstrumentedTest#pdfPreviewShowsBoundedPageAndAccessiblePreviousNextControls",
}
roots = (
    "app/build/outputs/androidTest-results/connected",
    "app/build/outputs/androidTest-results",
    "app/build/test-results",
)
files = []
for root in roots:
    files.extend(glob.glob(root + "/**/TEST-*.xml", recursive=True))

observed = {}
for path in sorted(set(files)):
    try:
        report = ET.parse(path).getroot()
    except ET.ParseError:
        continue
    for case in report.iter("testcase"):
        identifier = f"{case.get('classname', '?')}#{case.get('name', '?')}"
        if identifier not in expected:
            continue
        failure = case.find("failure")
        if failure is None:
            failure = case.find("error")
        assumption = "AssumptionViolatedException" in (
            (failure.get("message") or "") + (failure.text or "") + (failure.get("type") or "")
        ) if failure is not None else False
        if case.find("skipped") is not None or assumption:
            result = "SKIPPED"
        elif failure is not None:
            result = "FAILED"
            message = failure.get("message") or failure.text or ""
            # Keep actionable assertion text while avoiding file paths, URIs,
            # opaque IDs, and stack traces in Actions logs.
            message = re.sub(r"(?:content|file)://\S+", "<uri>", message)
            message = re.sub(r"(?<![A-Za-z0-9])/(?:[^/\s]+/)+[^/\s:]*", "<path>", message)
            message = re.sub(r"\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\b", "<id>", message)
            lines = [line.strip() for line in message.splitlines() if line.strip() and not line.lstrip().startswith("at ")]
            message = " ".join(lines[:2])[:400]
            print(f"PDF_TEST_FAILURE {identifier} type={failure.get('type', 'unknown')} message={message}")
        else:
            result = "PASSED"
        observed[identifier] = result

for identifier in sorted(expected):
    result = observed.get(identifier, "MISSING")
    print(f"PDF_TEST_RESULT {identifier} {result}")

not_passed = [identifier for identifier in expected if observed.get(identifier) != "PASSED"]
print(f"PDF_TESTS {len(observed)}/{len(expected)} passed={len(expected) - len(not_passed)}")
if not_passed:
    sys.exit("PDF instrumentation evidence incomplete: " + ", ".join(sorted(not_passed)))
PY
if [[ "${pdf_audit_status}" -ne 0 ]]; then
  status=1
fi

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
grep -E 'OmniPdf(Client|Worker|Init):' "${DIAGNOSTICS_DIR}/logcat.txt" | tail -n 150 | sed 's/^/PDF_WORKER_LOG /' || true
capture dumpsys-media-session.txt adb shell dumpsys media_session
capture dumpsys-omnifile-services.txt adb shell dumpsys activity services com.omnifile
capture dumpsys-audio-flinger.txt adb shell dumpsys media.audio_flinger
capture dumpsys-audio.txt adb shell dumpsys audio

exit "${status}"

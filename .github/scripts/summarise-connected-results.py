#!/usr/bin/env python3
"""Report and gate the instrumentation result of one API level of the matrix.

AGP already fails `connectedDebugAndroidTest` when a test genuinely fails, so
this script exists to answer three questions the Gradle exit code cannot:

  1. Did the suite actually execute anything? A run that silently discovers
     zero tests must never be reported as green, so a missing result file is a
     hard failure rather than an empty table.
  2. Did any test disappear? The total test count is compared against
     EXPECTED_MIN_TESTS, which is a floor rather than an exact value so that
     growing the suite never breaks CI.
  3. Is every skipped test accounted for? A skip is only legitimate when it is
     named in EXPECTED_SKIPS together with its reason; anything else is an
     unexplained loss of coverage and fails the job.

Assumption violations
---------------------
JUnit records a skipped test two different ways, and AGP mixes them. An
explicit `@Ignore` produces a `<skipped/>` element, but a test that declines to
run via `org.junit.Assume` produces a `<failure>` element whose body is an
`AssumptionViolatedException`. AGP's own exit-code logic does not count those
as failures -- on API 36 the run reported `test-result-exit-code.txt = 0` and
`BUILD SUCCESSFUL` for a suite whose XML said `failures="1"` -- so treating
them as failures here would invent a red build out of a green one.

Reinterpreting them as skips is therefore only correct while it stays exactly
as strict as AGP. It is: an assumption violation is only ever reclassified, and
if it is not named in EXPECTED_SKIPS it is still reported as unexplained
skipped coverage and fails the job. A genuinely new assumption-based skip
cannot slip in unnoticed, and a real failure is never reclassified.

Configuration is read from the environment so the workflow file stays the
single place where policy is declared:

  EXPECTED_MIN_TESTS  minimum number of test cases that must have been reported
  EXPECTED_SKIPS      comma separated `Class#method` entries that are allowed
                      to skip, each with a documented reason
  MATRIX_API_LEVEL    API level of the emulator, used only for reporting
"""

import glob
import os
import sys
import xml.etree.ElementTree as ET

# Ordered by preference. The first root that yields result files wins, so a
# stale unit-test report can never be mistaken for an instrumentation report.
RESULT_ROOTS = (
    "app/build/outputs/androidTest-results/connected",
    "app/build/outputs/androidTest-results",
    "app/build/test-results",
)

WALL_CLOCK_FILE = "emulator-diagnostics/wall-clock.txt"

ASSUMPTION_MARKER = "AssumptionViolatedException"


def find_result_files():
    """Return (root_used, [xml paths]) for the instrumentation run."""
    for root in RESULT_ROOTS:
        if not os.path.isdir(root):
            continue
        # Unit test results are never produced by this workflow, but excluding
        # them by path keeps a stale directory from inflating the count.
        files = [
            path
            for path in glob.glob(os.path.join(root, "**", "TEST-*.xml"), recursive=True)
            if "UnitTest" not in path
        ]
        if files:
            return root, sorted(files)
    return None, []


def _assumption_reason(node):
    """Extract the human reason from an AssumptionViolatedException body."""
    text = (node.get("message") or node.text or "").strip()
    first = text.splitlines()[0].strip() if text else ""
    if ASSUMPTION_MARKER + ":" in first:
        return first.split(ASSUMPTION_MARKER + ":", 1)[1].strip()
    return first or "assumption violated"


def parse(files):
    """Count test cases from JUnit XML without double counting nested suites."""
    passed = failed = skipped = 0
    total_seconds = 0.0
    failures = []
    skips = []
    suites = 0

    for path in files:
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError as exc:
            sys.exit(f"Unparseable instrumentation result {path}: {exc}")

        for suite in root.iter("testsuite"):
            suites += 1
            cases = list(suite.findall("testcase"))
            if not cases:
                # A suite that reports counts but no cases still has to count.
                reported = int(suite.get("tests", 0))
                bad = int(suite.get("failures", 0)) + int(suite.get("errors", 0))
                gone = int(suite.get("skipped", 0))
                passed += reported - bad - gone
                failed += bad
                skipped += gone
                continue

            for case in cases:
                name = f"{case.get('classname', '?')}#{case.get('name', '?')}"
                try:
                    total_seconds += float(case.get("time", 0.0))
                except ValueError:
                    pass

                error = case.find("failure")
                if error is None:
                    error = case.find("error")
                assumed = case.find("assumption")

                if case.find("skipped") is not None or assumed is not None:
                    reason = _assumption_reason(assumed if assumed is not None else case.find("skipped"))
                    skipped += 1
                    skips.append((name, reason))
                elif error is not None and ASSUMPTION_MARKER in (
                    (error.get("message") or "") + (error.text or "")
                    + (error.get("type") or "")
                ):
                    # Declined to run, not a defect. See the module docstring.
                    skipped += 1
                    skips.append((name, _assumption_reason(error)))
                elif error is not None:
                    failed += 1
                    failures.append((name, (error.get("message") or "").strip()))
                else:
                    passed += 1

    return {
        "suites": suites,
        "passed": passed,
        "failed": failed,
        "skipped": skipped,
        "tests": passed + failed + skipped,
        "seconds": total_seconds,
        "failures": failures,
        "skips": skips,
    }


def read_wall_clock():
    try:
        with open(WALL_CLOCK_FILE, encoding="utf-8") as handle:
            for line in handle:
                if line.startswith("instrumentation_wall_clock_seconds="):
                    return int(line.strip().split("=", 1)[1])
    except (OSError, ValueError):
        pass
    return None


def main():
    api_level = os.environ.get("MATRIX_API_LEVEL", "?")
    minimum = int(os.environ.get("EXPECTED_MIN_TESTS", "0"))
    allowed_skips = {
        entry.strip()
        for entry in os.environ.get("EXPECTED_SKIPS", "").split(",")
        if entry.strip()
    }

    root, files = find_result_files()
    if not files:
        sys.exit(
            "No instrumentation JUnit XML was found. The suite did not run, so "
            "this job cannot be reported as green. Searched: "
            + ", ".join(RESULT_ROOTS)
        )

    result = parse(files)
    wall_clock = read_wall_clock()

    print(f"API {api_level} instrumentation results (from {root})")
    fields = {
        key: result[key]
        for key in ("suites", "tests", "passed", "failed", "skipped")
    }
    fields["suite_time"] = f"{result['seconds']:.1f}s"
    fields["wall_clock"] = "unknown" if wall_clock is None else f"{wall_clock}s"
    print(
        "suites={suites} tests={tests} passed={passed} failed={failed} "
        "skipped={skipped} suite_time={suite_time} wall_clock={wall_clock}".format(**fields)
    )
    for name, message in result["failures"]:
        print(f"  FAILED  {name}")
        if message:
            print(f"          {message.splitlines()[0]}")
    for name, reason in result["skips"]:
        mark = "expected" if name in allowed_skips else "UNEXPLAINED"
        print(f"  SKIPPED [{mark}] {name}")
        print(f"          {reason}")

    problems = []
    if result["failed"]:
        problems.append(f"{result['failed']} test(s) failed")
    if result["tests"] < minimum:
        problems.append(
            f"only {result['tests']} test(s) reported, below the expected "
            f"floor of {minimum}"
        )
    unexplained = [name for name, _ in result["skips"] if name not in allowed_skips]
    if unexplained:
        problems.append(
            "unexplained skipped test(s): " + ", ".join(sorted(unexplained))
        )

    duration = f"{result['seconds']:.1f}s"
    if wall_clock is not None:
        duration += f" (job {wall_clock}s)"
    summary = (
        f"| {api_level} | {result['tests']} | {result['passed']} | "
        f"{result['failed']} | {result['skipped']} | {duration} |"
    )

    step_summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if step_summary:
        with open(step_summary, "a", encoding="utf-8") as handle:
            handle.write(f"### API {api_level} instrumentation\n\n")
            handle.write("| API | tests | passed | failed | skipped | duration |\n")
            handle.write("| --- | --- | --- | --- | --- | --- |\n")
            handle.write(summary + "\n\n")
            if result["skips"]:
                handle.write("Skipped:\n\n")
                for name, reason in sorted(result["skips"]):
                    state = (
                        "documented in EXPECTED_SKIPS"
                        if name in allowed_skips
                        else "**unexplained**"
                    )
                    handle.write(f"- `{name}` — {reason} ({state})\n")
                handle.write("\n")
            if result["failures"]:
                handle.write("Failed:\n\n")
                for name, message in sorted(result["failures"]):
                    detail = f": {message.splitlines()[0]}" if message else ""
                    handle.write(f"- `{name}`{detail}\n")
                handle.write("\n")

    if problems:
        for problem in problems:
            print(f"::error::{problem}")
        sys.exit(1)

    print(f"OK | {summary}")


if __name__ == "__main__":
    main()

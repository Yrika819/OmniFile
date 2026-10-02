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

# Only the canonical connected-test tree, cleared before each invocation, is evidence.
# Never fall back to unrelated variants or host/test-results from an earlier invocation.
RESULT_ROOTS = ("app/build/outputs/androidTest-results/connected",)

WALL_CLOCK_FILE = "emulator-diagnostics/wall-clock.txt"
EXCLUSIONS_FILE = "emulator-diagnostics/excluded-tests.txt"

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


def case_status(case):
    """Normalize the representations observed in actual Android/AGP reports."""
    markers = [child for child in case if child.tag in {"failure", "error", "skipped", "assumption"}]
    if len(markers) > 1:
        sys.exit("Ambiguous testcase status: multiple terminal markers")
    if not markers:
        return "PASSED", ""
    marker = markers[0]
    if marker.tag in {"skipped", "assumption"} or ASSUMPTION_MARKER in (
        (marker.get("message") or "") + (marker.text or "") + (marker.get("type") or "")
    ):
        return "SKIPPED", _assumption_reason(marker)
    return "FAILED", (marker.get("message") or "").strip()


def parse(files):
    """Unique concrete (classname, exact runner name) records are the only evidence.

    Suite totals describe roll-ups, never additional evidence. Repeated identity,
    even with the same status, makes an invocation ambiguous and fails closed.
    Parameter suffixes in runner-emitted names are preserved verbatim.
    """
    if not files:
        sys.exit("No instrumentation XML supplied")
    result = dict(suites=0, passed=0, failed=0, skipped=0, tests=0,
                  seconds=0.0, failures=[], skips=[], identities={})
    for path in files:
        try:
            root = ET.parse(path).getroot()
        except (ET.ParseError, OSError) as exc:
            sys.exit(f"Unparseable instrumentation result {path}: {exc}")
        if root.tag not in {"testsuite", "testsuites"}:
            sys.exit("Unsupported instrumentation XML root")
        for suite in root.iter():
            if suite.tag not in {"testsuite", "testsuites"}:
                continue
            if suite.tag == "testsuite":
                result["suites"] += 1
            cases = list(suite.iter("testcase"))
            concrete = len(cases)
            supported_counts = {
                "failures": sum(case.find("failure") is not None for case in cases),
                "errors": sum(case.find("error") is not None for case in cases),
                # A suite may count assumptions as failures or skips; both are backed by records.
                "skipped": sum(case_status(case)[0] == "SKIPPED" for case in cases),
            }
            for field in ("tests", "failures", "errors", "skipped"):
                try:
                    count = int(suite.get(field, "0"))
                except ValueError:
                    sys.exit(f"Invalid suite {field} count")
                if count < 0:
                    sys.exit(f"Negative suite {field} count")
                if concrete == 0 and count > 0:
                    sys.exit(f"Positive anonymous suite {field} count without concrete cases")
                if field != "tests" and count > supported_counts[field]:
                    sys.exit(f"Suite {field} count lacks concrete status evidence")
                if field == "tests" and field in suite.attrib and count != concrete:
                    sys.exit("Suite test count disagrees with concrete testcase records")
        for case in root.iter("testcase"):
            identity = (case.get("classname"), case.get("name"))
            if any(value is None or not value.strip() for value in identity):
                sys.exit("Concrete testcase requires classname and name")
            name = "#".join(identity)
            status, detail = case_status(case)
            prior = result["identities"].get(identity)
            if prior is not None:
                sys.exit(f"Duplicate testcase identity: {name} ({prior} / {status})")
            result["identities"][identity] = status
            result[status.lower()] += 1
            result["tests"] += 1
            if status == "FAILED":
                result["failures"].append((name, detail))
            elif status == "SKIPPED":
                result["skips"].append((name, detail))
            try:
                result["seconds"] += float(case.get("time", "0"))
            except ValueError:
                pass
    return result


MANDATORY_CONTRACTS = (
    "required-vs10-instrumentation.txt",
    "required-post-vs10-instrumentation.txt",
    "required-vs11-instrumentation.txt",
)


def audit_mandatory(result, contracts=MANDATORY_CONTRACTS):
    expected = set()
    for contract in contracts:
        with open(os.path.join(os.path.dirname(__file__), contract), encoding="utf-8") as handle:
            expected.update(line.strip() for line in handle if line.strip())
    observed = {"#".join(identity): status for identity, status in result["identities"].items()}
    not_passed = []
    for name in sorted(expected):
        status = observed.get(name, "MISSING")
        print(f"PDF_TEST_RESULT {name} {status}")
        if status != "PASSED":
            not_passed.append(name)
    print(f"PDF_TESTS {len(expected) - len(not_passed)}/{len(expected)} passed={len(expected) - len(not_passed)}")
    return ["Mandatory instrumentation evidence incomplete: " + ", ".join(not_passed)] if not_passed else []


def read_exclusions():
    """Tests the CI deliberately did not run on this API level, if any."""
    try:
        with open(EXCLUSIONS_FILE, encoding="utf-8") as handle:
            return [line.strip() for line in handle if line.strip()]
    except OSError:
        return []


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
    mandatory_problems = audit_mandatory(result)
    if "--audit-mandatory" in sys.argv:
        if mandatory_problems:
            sys.exit("; ".join(mandatory_problems))
        return
    wall_clock = read_wall_clock()
    exclusions = read_exclusions()

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
    for name in exclusions:
        print(f"  EXCLUDED (documented cloud-emulator limitation) {name}")

    problems = list(mandatory_problems)
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
            if exclusions:
                handle.write("Excluded from cloud CI on this API level:\n\n")
                for name in exclusions:
                    handle.write(f"- `{name}`\n")
                handle.write("\n")
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

"""Disposable P05-001 host-side planning/fixture harness.

POC-ONLY — NOT PRODUCTION AUTHORITY
"""

import argparse
import json
from dataclasses import dataclass
from typing import Iterable, List


@dataclass(frozen=True)
class Fixture:
    operation: str
    executor: str
    destination: str
    source_present: bool
    api_level: int = 31
    stop_event: str = "PROCESS_DEATH"


@dataclass(frozen=True)
class Decision:
    classification: str
    source_delete_allowed: bool


def classify_fixture(fixture: Fixture) -> Decision:
    if fixture.destination == "PARTIAL":
        return Decision("RESUMABLE", False)
    if fixture.destination == "AMBIGUOUS_FINALIZATION":
        return Decision("NEEDS_ATTENTION", False)
    if fixture.destination == "VERIFIED_FINAL":
        return Decision("COMPLETE", fixture.operation == "MOVE" and fixture.source_present)
    return Decision("RESTART_REQUIRED", False)


def default_fixtures() -> List[Fixture]:
    return [
        Fixture("MOVE", "IN_APP_FOREGROUND_SHAPED", "PARTIAL", True, 31),
        Fixture("MOVE", "UIDT_SHAPED", "AMBIGUOUS_FINALIZATION", True, 34, "EXTERNAL_STOP"),
        Fixture("MOVE", "WORK_MANAGER_SHAPED", "VERIFIED_FINAL", True, 36),
        Fixture("MOVE", "FGS_DATA_SYNC_SHAPED", "PARTIAL", True, 31),
        Fixture("COPY", "FGS_MEDIA_PROCESSING_SHAPED", "VERIFIED_FINAL", True, 35),
        Fixture("COPY", "UNRESOLVED_LOCAL_COPY_SHAPED", "UNRESOLVED", True, 36),
    ]


def run_fixture_report(fixtures: Iterable[Fixture]) -> dict:
    observations = []
    for fixture in fixtures:
        decision = classify_fixture(fixture)
        observations.append(
            {
                "api_level": fixture.api_level,
                "executor_shape": fixture.executor,
                "operation": fixture.operation,
                "stop_event": fixture.stop_event,
                "destination_reality": fixture.destination,
                "classification": decision.classification,
                "source_present": fixture.source_present,
                "source_delete_allowed": decision.source_delete_allowed,
                "platform_runtime_observed": False,
            }
        )
    return {
        "label": "POC-ONLY — NOT PRODUCTION AUTHORITY",
        "authority": "PLANNING_FIXTURE_ONLY",
        "platform_runtime_observed": False,
        "adb_used": False,
        "selected_executor": None,
        "observations": observations,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="Run the P05-001 fixture harness")
    parser.add_argument("--json", action="store_true", help="emit machine-readable JSON")
    args = parser.parse_args()
    report = run_fixture_report(default_fixtures())
    if args.json:
        print(json.dumps(report, sort_keys=True, ensure_ascii=False))
        return
    print(report["label"])
    print("authority=" + report["authority"])
    for observation in report["observations"]:
        print(
            "{api_level} {executor_shape} {operation} {classification} "
            "source_delete_allowed={source_delete_allowed}".format(**observation)
        )


if __name__ == "__main__":
    main()

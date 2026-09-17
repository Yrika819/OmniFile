import unittest

from p05_001_executor_harness import (
    Fixture,
    classify_fixture,
    default_fixtures,
    run_fixture_report,
)


class ExecutorRecoveryTests(unittest.TestCase):
    def test_move_keeps_source_when_destination_is_only_partial(self):
        fixture = Fixture("MOVE", "UIDT_SHAPED", "PARTIAL", True)
        decision = classify_fixture(fixture)
        self.assertEqual(decision.classification, "RESUMABLE")
        self.assertFalse(decision.source_delete_allowed)

    def test_ambiguous_finalization_blocks_source_deletion(self):
        fixture = Fixture("MOVE", "FGS_DATA_SYNC_SHAPED", "AMBIGUOUS_FINALIZATION", True)
        decision = classify_fixture(fixture)
        self.assertEqual(decision.classification, "NEEDS_ATTENTION")
        self.assertFalse(decision.source_delete_allowed)

    def test_verified_final_allows_the_move_source_delete_step(self):
        fixture = Fixture("MOVE", "WORK_MANAGER_SHAPED", "VERIFIED_FINAL", True)
        decision = classify_fixture(fixture)
        self.assertEqual(decision.classification, "COMPLETE")
        self.assertTrue(decision.source_delete_allowed)

    def test_report_identifies_fixture_scope_and_keeps_executor_unselected(self):
        report = run_fixture_report(default_fixtures())
        self.assertEqual(report["authority"], "PLANNING_FIXTURE_ONLY")
        self.assertFalse(report["platform_runtime_observed"])
        self.assertFalse(report["adb_used"])
        self.assertIsNone(report["selected_executor"])
        self.assertGreaterEqual(len(report["observations"]), 4)

    def test_report_covers_executor_shapes_without_runtime_claims(self):
        report = run_fixture_report(default_fixtures())
        self.assertEqual(
            {observation["executor_shape"] for observation in report["observations"]},
            {
                "IN_APP_FOREGROUND_SHAPED",
                "WORK_MANAGER_SHAPED",
                "UIDT_SHAPED",
                "FGS_DATA_SYNC_SHAPED",
                "FGS_MEDIA_PROCESSING_SHAPED",
                "UNRESOLVED_LOCAL_COPY_SHAPED",
            },
        )
        self.assertTrue(
            all(
                observation["platform_runtime_observed"] is False
                for observation in report["observations"]
            )
        )


if __name__ == "__main__":
    unittest.main()

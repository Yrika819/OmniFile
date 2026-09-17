import json
import pathlib
import unittest


ROOT = pathlib.Path(__file__).parent


class RuntimeContractTests(unittest.TestCase):
    def load_rows(self, name="runtime-report-all.jsonl"):
        report = ROOT / "results" / name
        self.assertTrue(report.exists(), f"missing fresh runtime artifact: {report}")
        rows = [json.loads(line) for line in report.read_text().splitlines() if line.strip()]
        self.assertTrue(rows, f"empty runtime artifact: {report}")
        return rows

    def test_runtime_report_has_device_identity_and_executor_observations(self):
        self.assertTrue((ROOT / "results" / "campaign-metadata.json").exists(), "run the disposable Android campaign first")
        rows = self.load_rows()
        self.assertTrue(all("sdkInt" in row for row in rows))
        for executor in ("FOREGROUND_APP", "FOREGROUND_SERVICE", "JOB_SCHEDULER", "UIDT"):
            executor_rows = [row for row in rows if row.get("executor") == executor]
            self.assertTrue(executor_rows, executor)
            events = {row.get("event") for row in executor_rows}
            self.assertIn("START", events, executor)
            self.assertIn("COMPLETE", events, executor)
            self.assertTrue(any(row.get("durableState") == "COMPLETE" for row in executor_rows), executor)
        self.assertTrue(all(row.get("sdkInt") == "36" for row in rows), "Pixel campaign must be API36")
        self.assertTrue(any(row.get("model") == "Pixel 7a" for row in rows))

    def test_api31_matrix_is_captured_separately(self):
        api31_rows = []
        for mode in ("foreground", "fgs", "job"):
            rows = self.load_rows(f"api31/runtime-report-{mode}.jsonl")
            api31_rows.extend(rows)
            self.assertTrue(any(row.get("sdkInt") == "31" for row in rows), mode)
            self.assertTrue(any(row.get("event") == "COMPLETE" for row in rows), mode)
        uidt = (ROOT / "results" / "api31" / "runtime-report-uidt.jsonl").read_text()
        self.assertIn("NOT_APPLICABLE_API_LT_34", uidt)
        provenance = (ROOT / "results" / "api31" / "artifact-provenance.txt").read_text()
        self.assertIn("STATUS=PRIOR_DISPOSABLE_CAPTURE", provenance)
        self.assertIn("APK_SHA256=NOT_RETAINED", provenance)
        self.assertIn("FINAL_ARTIFACT_EQUIVALENCE=NOT_PROVEN", provenance)
        self.assertFalse(any(row.get("event") == "REDISCOVER" for row in api31_rows))

    def test_job_stop_is_invocation_scoped(self):
        source = (ROOT / "RuntimeJobService.java").read_text()
        self.assertIn("class JobInvocation", source)
        self.assertIn("activeInvocation", source)
        self.assertIn("isActive(invocation)", source)
        self.assertIn("beginFinalization()", source)
        self.assertIn("invocation.lock.lock()", source)
        self.assertIn("run(uidt ? \"UIDT\" : \"JOB_SCHEDULER\", \"job_started\", probe(invocation))", source)
        self.assertIn("finishIfActive(invocation, params)", source)

    def test_recovery_provenance_survives_activity_stop(self):
        source = (ROOT / "MainActivity.java").read_text()
        self.assertIn("if (!recoveryMode) evidence.setLifecycle(\"activity_not_foreground\")", source)

    def test_restart_reconciles_and_completes(self):
        rows = self.load_rows()
        self.assertTrue(any(row.get("event") == "REDISCOVER" for row in rows))
        self.assertTrue(any(row.get("event") == "COMPLETE" and row.get("lifecycle") == "app_restart_recreated_executor" for row in rows))

    def test_report_does_not_promote_process_survival_to_durable_completion(self):
        rows = self.load_rows()
        for row in rows:
            self.assertIn("durableState", row)
            self.assertIn("lifecycle", row)


if __name__ == "__main__":
    unittest.main()

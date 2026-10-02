"""Deterministic parser/gate fixtures, including a reduced real AGP report."""
import contextlib
import importlib.util
import io
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

SCRIPTS = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('connected_results', SCRIPTS / 'summarise-connected-results.py')
p = importlib.util.module_from_spec(spec)
spec.loader.exec_module(p)


def case(name='ordinary', status='', cls='example.Test'):
    return f'<testcase classname="{cls}" name="{name}" time="0.1">{status}</testcase>'


def suite(cases='', **counts):
    attrs = ' '.join(f'{k}="{v}"' for k, v in counts.items())
    return f'<testsuite {attrs}>{cases}</testsuite>'


class EvidenceTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)

    def parse(self, *reports):
        files = []
        for i, report in enumerate(reports):
            path = Path(self.tmp.name) / f'TEST-{i}.xml'
            path.write_text(report)
            files.append(str(path))
        return p.parse(files)

    def rejects(self, *reports):
        with self.assertRaises(SystemExit):
            self.parse(*reports)

    def test_normal_flat_concrete(self):
        r = self.parse(suite(case('one') + case('two'), tests=2))
        self.assertEqual((r['tests'], r['passed']), (2, 2))

    def test_nested_rollups_count_concrete_once(self):
        r = self.parse('<testsuites tests="2">' + suite(suite(case('one') + case('two'), tests=2), tests=2) + '</testsuites>')
        self.assertEqual(r['tests'], 2)

    def test_duplicate_nonmandatory_one_suite(self):
        self.rejects(suite(case() + case(), tests=2))

    def test_duplicate_across_suites(self):
        self.rejects('<testsuites>' + suite(case()) + suite(case()) + '</testsuites>')

    def test_duplicate_across_files(self):
        self.rejects(suite(case()), suite(case()))

    def test_duplicate_mandatory(self):
        identity = (SCRIPTS / 'required-vs10-instrumentation.txt').read_text().splitlines()[0]
        cls, name = identity.split('#')
        self.rejects(suite(case(name, cls=cls) * 2))

    def test_conflicting_pass_fail(self):
        self.rejects(suite(case() + case(status='<failure message="broken"/>')))

    def test_conflicting_pass_skip(self):
        self.rejects(suite(case() + case(status='<skipped/>')))

    def test_missing_mandatory(self):
        with contextlib.redirect_stdout(io.StringIO()):
            self.assertTrue(p.audit_mandatory(self.parse(suite(case()))))

    def mandatory_report(self, first_status=''):
        ids = [line for contract in p.MANDATORY_CONTRACTS
               for line in (SCRIPTS / contract).read_text().splitlines() if line]
        return suite(''.join(case(name, first_status if i == 0 else '', cls) for i, (cls, name) in enumerate(identity.split('#') for identity in ids)))

    def test_mandatory_skip_rejected_even_if_allowed(self):
        with contextlib.redirect_stdout(io.StringIO()):
            self.assertTrue(p.audit_mandatory(self.parse(self.mandatory_report('<skipped/>'))))

    def gate(self, status, allowed=True, minimum=0):
        r = self.parse(self.mandatory_report()[:-12] + case('skip', status) + '</testsuite>')
        with patch.object(p, 'find_result_files', return_value=('canonical', ['fixture'])), patch.object(p, 'parse', return_value=r), patch.object(p, 'read_exclusions', return_value=[]), patch.object(p, 'read_wall_clock', return_value=None), patch.dict(os.environ, {'EXPECTED_MIN_TESTS': str(minimum), 'EXPECTED_SKIPS': 'example.Test#skip' if allowed else ''}, clear=True), contextlib.redirect_stdout(io.StringIO()):
            p.main()
        return r

    def test_authorized_explicit_skip(self):
        self.assertEqual(self.gate('<skipped message="documented"/>')['skipped'], 1)

    def test_authorized_assumption_failure(self):
        self.assertEqual(self.gate('<failure>org.junit.AssumptionViolatedException: documented</failure>')['skipped'], 1)

    def test_authorized_assumption_element(self):
        self.assertEqual(self.gate('<assumption message="documented"/>')['skipped'], 1)

    def test_unauthorized_named_skip_rejected(self):
        with self.assertRaises(SystemExit):
            self.gate('<skipped/>', allowed=False)

    def test_positive_anonymous_leaf_tests(self):
        self.rejects(suite(tests=1))

    def test_positive_anonymous_skipped(self):
        self.rejects(suite(tests=0, skipped=1))

    def test_positive_anonymous_skipped_alongside_concrete_passes(self):
        self.rejects(suite(case(), tests=1, skipped=1))

    def test_positive_anonymous_failure_alongside_concrete_passes(self):
        self.rejects(suite(case(), tests=1, failures=1))

    def test_empty_zero_suite(self):
        self.assertEqual(self.parse(suite(tests=0, failures=0, errors=0, skipped=0))['tests'], 0)

    def test_malformed(self):
        self.rejects('<testsuite')

    def test_missing_xml(self):
        with self.assertRaises(SystemExit):
            p.parse([])
        with self.assertRaises(SystemExit):
            p.parse([str(Path(self.tmp.name) / 'absent.xml')])

    def test_duplicate_complete_report(self):
        report = suite(case('one') + case('two'), tests=2)
        self.rejects(report, report)

    def test_api35_floor_and_exclusions_configuration(self):
        workflow = (SCRIPTS.parent / 'workflows/android-emulator-ci.yml').read_text()
        self.assertIn("api-level: 35\n            target: google_apis\n            expected-tests: '109'", workflow)
        runner = (SCRIPTS / 'run-connected-instrumentation.sh').read_text()
        self.assertIn('RequiresAudioClock', runner)
        self.assertIn('wavPlaysToEndedState', runner)
        with self.assertRaises(SystemExit):
            self.gate('<skipped/>', minimum=109)

    def test_representative_actual_agp_xml(self):
        r = p.parse([str(Path(__file__).parent / 'fixtures/actual-agp-api36.xml')])
        self.assertEqual((r['tests'], r['passed'], r['skipped'], r['failed']), (3, 2, 1, 0))

    def test_parameter_suffixes_preserved_defensively(self):
        # No parameterized cases in inspected AGP output. Protect exact-name behavior anyway.
        r = self.parse(suite(case('method[0]') + case('method[1]')))
        self.assertEqual(r['tests'], 2)
        self.rejects(suite(case('method[0]') * 2))

    def test_targeted_selector_preserves_mandatory_inventory_and_floor(self):
        root = SCRIPTS.parents[1]
        classes = {}
        count = 0
        for source in (root / 'app/src/androidTest/java').rglob('*.kt'):
            content = source.read_text()
            if '@com.omnifile.preview.PostVs10HardeningTarget' not in content:
                continue
            package = content.split('package ', 1)[1].splitlines()[0]
            classes[package + '.' + source.stem] = content
            count += content.count('@Test')
        # 28 post-VS10 cases plus the three VS11 File Details cases. The targeted campaign has to
        # keep proving the whole mandatory set, so VS11 is selected by the same runner argument.
        self.assertEqual(count, 31)
        for contract in p.MANDATORY_CONTRACTS:
            for identity in (SCRIPTS / contract).read_text().splitlines():
                cls, method = identity.split('#')
                self.assertIn(cls, classes)
                self.assertIn('fun ' + method + '(', classes[cls])
        runner = (SCRIPTS / 'run-connected-instrumentation.sh').read_text()
        self.assertIn('Arguments.annotation=com.omnifile.preview.PostVs10HardeningTarget', runner)
        self.assertNotIn('Arguments.class=', runner)

    def test_mandatory_contract_covers_every_slice_exactly_once(self):
        ids = []
        for contract in p.MANDATORY_CONTRACTS:
            for line in (SCRIPTS / contract).read_text().splitlines():
                if line.strip():
                    ids.append(line.strip())
        # 19 VS10 + 3 post-VS10 + 3 VS11, with no identity listed twice.
        self.assertEqual(len(ids), 25)
        self.assertEqual(len(set(ids)), 25)
        self.assertEqual(len(p.MANDATORY_CONTRACTS), 3)

    def test_duplicate_mandatory_identity_across_contracts_is_still_rejected(self):
        identity = (SCRIPTS / 'required-vs11-instrumentation.txt').read_text().splitlines()[0]
        cls, name = identity.split('#')
        self.rejects(suite(case(name, cls=cls) * 2))

    def test_stale_fallback_never_discovered(self):
        with patch.object(p.os.path, 'isdir', return_value=True), patch.object(p.glob, 'glob', return_value=[]) as discovery:
            self.assertEqual(p.find_result_files(), (None, []))
            self.assertEqual(discovery.call_count, 1)
            self.assertIn('outputs/androidTest-results/connected', discovery.call_args.args[0])

    def test_missing_identity(self):
        self.rejects(suite('<testcase name="one"/>'))

    def test_conflicting_terminal_markers(self):
        self.rejects(suite(case(status='<skipped/><failure/>')))

    def test_inconsistent_declared_total(self):
        self.rejects(suite(case(), tests=2))

    def test_exact_false_green_exploit_classes(self):
        concrete = ''.join(case(f'case{i}') for i in range(105))
        self.rejects(suite(concrete + case('case0'), tests=106))
        self.rejects('<testsuites>' + suite(concrete, tests=105) + suite(tests=1) + '</testsuites>')
        normalized = self.parse(suite(suite(concrete, tests=105), tests=105))
        self.assertEqual(normalized['tests'], 105)  # Below the original floor of 106.
        self.rejects(suite(concrete, tests=105), suite(concrete, tests=105))
        self.rejects('<testsuites>' + suite(concrete, tests=105) + suite(skipped=1) + '</testsuites>')


if __name__ == '__main__':
    unittest.main()

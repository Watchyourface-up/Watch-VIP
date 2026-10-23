"""Unit tests for tools/inspect_autolinking.py diagnostics."""
from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).resolve().parents[1] / "tools" / "inspect_autolinking.py"
SPEC = importlib.util.spec_from_file_location("inspect_autolinking", MODULE_PATH)
assert SPEC and SPEC.loader
inspector = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(inspector)


class InspectAutolinkingTests(unittest.TestCase):
    def test_valid_publication_passes(self) -> None:
        payload = {
            "modules": [{
                "packageName": "example-module",
                "packageVersion": "1.2.3",
                "projects": [{
                    "name": "example-module",
                    "publication": {
                        "group": "org.example",
                        "id": "example-module",
                        "version": "1.2.3",
                        "repository": "maven",
                    },
                }],
            }]
        }
        self.assertEqual(inspector.inspect(payload), [])

    def test_missing_publication_version_names_module_and_field(self) -> None:
        payload = {
            "modules": [{
                "packageName": "broken-module",
                "packageVersion": "2.0.0",
                "projects": [{
                    "name": "broken-project",
                    "publication": {
                        "group": "org.example",
                        "id": "broken",
                        "repository": "maven",
                    },
                }],
            }]
        }
        issues = inspector.inspect(payload)
        self.assertEqual(len(issues), 1)
        self.assertIn("broken-module@2.0.0", issues[0])
        self.assertIn("'version'", issues[0])

    def test_null_publication_is_allowed(self) -> None:
        self.assertEqual(
            inspector.inspect({"modules": [{"packageName": "module", "projects": [{"publication": None}]}]}),
            [],
        )

    def test_invalid_root_reports_clear_error(self) -> None:
        self.assertTrue(inspector.inspect([])[0].startswith("Autolinking output root"))


if __name__ == "__main__":
    unittest.main()

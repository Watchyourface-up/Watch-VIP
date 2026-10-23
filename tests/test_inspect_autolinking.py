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
    def test_valid_sdk54_module_and_publication_pass(self) -> None:
        payload = {"modules": [{
            "packageName": "expo-font",
            "packageVersion": "14.0.12",
            "projects": [{
                "name": "expo-font",
                "publication": {
                    "groupId": "host.exp.exponent",
                    "artifactId": "expo.modules.font",
                    "version": "14.0.12",
                    "repository": "local-maven-repo",
                },
            }],
        }]}
        self.assertEqual(inspector.inspect(payload, {}, {"expo-font": "~14.0.12"}), [])

    def test_sdk54_rejects_font_from_different_sdk_line(self) -> None:
        payload = {"modules": [{
            "packageName": "expo-font",
            "packageVersion": "58.0.9",
            "projects": [{"name": "expo-font", "publication": {
                "groupId": "host.exp.exponent",
                "artifactId": "expo.modules.font",
                "version": "58.0.9",
                "repository": "local-maven-repo",
            }}],
        }]}
        issues = inspector.inspect(payload, {}, {"expo-font": "~14.0.12"})
        self.assertEqual(len(issues), 1)
        self.assertIn("expo-font@58.0.9", issues[0])
        self.assertIn("~14.0.12", issues[0])

    def test_missing_publication_version_is_reported_without_guess(self) -> None:
        payload = {"modules": [{
            "packageName": "expo-font",
            "packageVersion": "14.0.12",
            "projects": [{"name": "expo-font", "publication": {
                "groupId": "host.exp.exponent",
                "artifactId": "expo.modules.font",
                "repository": "local-maven-repo",
            }}],
        }]}
        issues = inspector.inspect(payload, {}, {"expo-font": "~14.0.12"})
        self.assertEqual(len(issues), 1)
        self.assertIn("'version'", issues[0])

    def test_typescript_field_aliases_are_supported(self) -> None:
        payload = {"modules": [{
            "packageName": "expo-font",
            "packageVersion": "14.0.12",
            "projects": [{"publication": {
                "group": "host.exp.exponent",
                "id": "expo.modules.font",
                "version": "14.0.12",
                "repository": "local-maven-repo",
            }}],
        }]}
        self.assertEqual(inspector.inspect(payload, {}, {"expo-font": "~14.0.12"}), [])

    def test_null_publication_is_allowed(self) -> None:
        self.assertEqual(
            inspector.inspect({"modules": [{"packageName": "module", "projects": [{"publication": None}]}]}, {}, {}),
            [],
        )

    def test_invalid_root_reports_clear_error(self) -> None:
        self.assertTrue(inspector.inspect([], {}, {})[0].startswith("Autolinking output root"))


if __name__ == "__main__":
    unittest.main()

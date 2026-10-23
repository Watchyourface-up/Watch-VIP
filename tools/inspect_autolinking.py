#!/usr/bin/env python3
"""Validate the Expo Android autolinking metadata used by the native Gradle plugin.

This catches SDK-version drift and malformed publication data before Gradle's
Kotlin deserializer emits an opaque index-only error. It never mutates packages
or fabricates missing publication versions.
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from typing import Any

REQUIRED_FIELDS = ("groupId", "artifactId", "version", "repository")
FIELD_ALIASES = {"groupId": "group", "artifactId": "id"}
ROOT = Path(__file__).resolve().parents[1]


def version_matches(spec: str, version: str) -> bool:
    """Support the simple exact, ~ and ^ versions used in Expo's bundled module map."""
    spec = spec.strip()
    if spec.startswith("~"):
        base = spec[1:]
        parts = base.split(".")
        actual = version.split(".")
        return len(parts) >= 2 and len(actual) >= len(parts) and actual[:len(parts)-1] == parts[:len(parts)-1] and actual >= parts
    if spec.startswith("^"):
        base = spec[1:]
        parts = base.split(".")
        actual = version.split(".")
        return bool(parts and actual) and actual[0] == parts[0] and actual >= parts
    return version == spec


def inspect(payload: Any, package_json: dict[str, Any], bundled: dict[str, Any]) -> list[str]:
    if not isinstance(payload, dict):
        return ["Autolinking output root must be a JSON object."]
    modules = payload.get("modules")
    if not isinstance(modules, list):
        return ["Autolinking output has no modules array."]

    issues: list[str] = []
    for module_index, module in enumerate(modules):
        if not isinstance(module, dict):
            issues.append(f"modules[{module_index}] is not an object.")
            continue
        package = module.get("packageName", "<unknown package>")
        package_version = module.get("packageVersion", "<unknown package version>")
        if isinstance(package, str) and isinstance(package_version, str):
            required = bundled.get(package)
            if isinstance(required, str) and not version_matches(required, package_version):
                issues.append(
                    f"modules[{module_index}] {package}@{package_version} is outside Expo SDK 54 "
                    f"bundled range {required}; update the dependency in package.json to the SDK 54-compatible version."
                )

        projects = module.get("projects", [])
        if not isinstance(projects, list):
            issues.append(f"modules[{module_index}] ({package}@{package_version}).projects is not an array.")
            continue
        for project_index, project in enumerate(projects):
            if not isinstance(project, dict):
                issues.append(f"modules[{module_index}] ({package}@{package_version}).projects[{project_index}] is not an object.")
                continue
            publication = project.get("publication")
            if publication is None:
                continue
            if not isinstance(publication, dict):
                issues.append(
                    f"modules[{module_index}] ({package}@{package_version}).projects[{project_index}].publication "
                    "must be an object when present."
                )
                continue
            missing = []
            for field in REQUIRED_FIELDS:
                alias = FIELD_ALIASES.get(field)
                value = publication.get(field, publication.get(alias) if alias else None)
                if not isinstance(value, str) or not value.strip():
                    missing.append(field)
            if missing:
                safe = {
                    key: value for key, value in publication.items()
                    if key.lower() not in {"password", "token", "secret", "credential", "credentials"}
                }
                issues.append(
                    f"modules[{module_index}] ({package}@{package_version}).projects[{project_index}] "
                    f"name={project.get('name', '<unnamed>')!r}: publication missing/empty fields {missing}; "
                    f"observed publication={json.dumps(safe, sort_keys=True)}"
                )
    return issues


def load_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def main() -> int:
    if len(sys.argv) != 2:
        print(f"Usage: {Path(sys.argv[0]).name} AUTOLINKING_JSON", file=sys.stderr)
        return 2
    try:
        payload = load_json(Path(sys.argv[1]))
        package_json = load_json(ROOT / "package.json")
        expo_manifest = load_json(ROOT / "node_modules" / "expo" / "bundledNativeModules.json")
    except (OSError, json.JSONDecodeError) as exc:
        print(f"Unable to load autolinking/dependency metadata: {exc}", file=sys.stderr)
        return 2

    issues = inspect(payload, package_json, expo_manifest)
    if issues:
        print("Expo Android autolinking validation failed:")
        for issue in issues:
            print(f"- {issue}")
        return 1
    print("Expo SDK 54 Android autolinking metadata: OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

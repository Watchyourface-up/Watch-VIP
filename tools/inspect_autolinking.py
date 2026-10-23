#!/usr/bin/env python3
"""Inspect Expo Android autolinking publication metadata.

The Gradle settings plugin decodes the result of:
  expo-modules-autolinking resolve --platform android --json

The script reports each module/project and the publication keys that are present.
It is a read-only diagnostic: it never adds a guessed publication version.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any

# Expo's TS and Gradle schemas use different serialized names in SDK 54.
# Report missing properties based on the shape that Gradle expects.
REQUIRED_FIELDS = ("groupId", "artifactId", "version", "repository")
FIELD_ALIASES = {"groupId": "group", "artifactId": "id"}


def inspect(payload: Any) -> list[str]:
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
        projects = module.get("projects", [])
        if not isinstance(projects, list):
            issues.append(f"modules[{module_index}] ({package}@{package_version}).projects is not an array.")
            continue

        for project_index, project in enumerate(projects):
            if not isinstance(project, dict):
                issues.append(
                    f"modules[{module_index}] ({package}@{package_version}).projects[{project_index}] is not an object."
                )
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
                value = publication.get(field, publication.get(FIELD_ALIASES.get(field, "")))
                if not isinstance(value, str) or not value.strip():
                    missing.append(field)

            if missing:
                safe_publication = {
                    key: value for key, value in publication.items()
                    if key.lower() not in {"password", "token", "secret", "credential", "credentials"}
                }
                issues.append(
                    f"modules[{module_index}] ({package}@{package_version}).projects[{project_index}] "
                    f"name={project.get('name', '<unnamed>')!r}: publication missing/empty fields {missing}; "
                    f"observed publication={json.dumps(safe_publication, sort_keys=True)}"
                )
    return issues


def main() -> int:
    if len(sys.argv) != 2:
        print(f"Usage: {Path(sys.argv[0]).name} AUTOLINKING_JSON", file=sys.stderr)
        return 2
    path = Path(sys.argv[1])
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        print(f"Unable to read valid autolinking JSON from {path}: {exc}", file=sys.stderr)
        return 2

    issues = inspect(payload)
    if issues:
        print("Invalid Expo Android autolinking metadata:")
        for issue in issues:
            print(f"- {issue}")
        return 1
    print("Expo Android autolinking publication metadata: OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

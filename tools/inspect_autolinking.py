#!/usr/bin/env python3
"""Validate Expo Android autolinking JSON against the Gradle Publication schema.

Usage:
  npx expo-modules-autolinking resolve --platform android --json > /tmp/devvault-autolinking.json
  python tools/inspect_autolinking.py /tmp/devvault-autolinking.json

The Gradle plugin consumes the JSON emitted by this command. The expected serialized
publication keys are groupId, artifactId, version and repository. This tool is
read-only; it never fabricates publication metadata or edits installed packages.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any

REQUIRED_PUBLICATION_FIELDS = ("groupId", "artifactId", "version", "repository")


def inspect(payload: Any) -> list[str]:
    issues: list[str] = []
    if not isinstance(payload, dict):
        return ["Autolinking output root must be a JSON object."]

    modules = payload.get("modules")
    if not isinstance(modules, list):
        return ["Autolinking output has no modules array."]

    for module_index, module in enumerate(modules):
        if not isinstance(module, dict):
            issues.append(f"modules[{module_index}] is not an object.")
            continue
        package = module.get("packageName", "<unknown package>")
        version = module.get("packageVersion", "<unknown package version>")
        projects = module.get("projects", [])
        if not isinstance(projects, list):
            issues.append(f"modules[{module_index}] ({package}@{version}).projects is not an array.")
            continue

        for project_index, project in enumerate(projects):
            if not isinstance(project, dict):
                issues.append(
                    f"modules[{module_index}] ({package}@{version}).projects[{project_index}] is not an object."
                )
                continue
            publication = project.get("publication")
            if publication is None:
                continue
            if not isinstance(publication, dict):
                issues.append(
                    f"modules[{module_index}] ({package}@{version}).projects[{project_index}].publication "
                    "must be an object when present."
                )
                continue
            missing = [
                key for key in REQUIRED_PUBLICATION_FIELDS
                if not isinstance(publication.get(key), str) or not publication[key].strip()
            ]
            if missing:
                safe_publication = {
                    key: value for key, value in publication.items()
                    if key.lower() not in {"password", "token", "secret", "credential", "credentials"}
                }
                issues.append(
                    f"modules[{module_index}] ({package}@{version}).projects[{project_index}] "
                    f"name={project.get('name', '<unnamed>')!r}: publication missing/empty "
                    f"fields {missing}; observed publication={json.dumps(safe_publication, sort_keys=True)}"
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

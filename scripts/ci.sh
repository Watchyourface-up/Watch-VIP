#!/usr/bin/env bash
set -euo pipefail
npm install
npm run typecheck
npm test
npm run lint
python3 tools/validate_schema.py
python3 tools/check_secrets.py

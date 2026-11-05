#!/usr/bin/env bash
set -euo pipefail
set +x
export PYTHONDONTWRITEBYTECODE=1
exec python3 -B ops/postgres/hba_local_test.py "$@"

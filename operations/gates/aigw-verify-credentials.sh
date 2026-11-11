#!/usr/bin/env bash
# 검증 수용 자격(Plan §11.7·P15): provision|check|rotate --stage F1..F4, cleanup. 값 출력·저장 0 — 본체는 verify_credentials.py.
set -euo pipefail
set +x
export PYTHONDONTWRITEBYTECODE=1
[[ -f operations/deploy/helm/ai-gateway-db-restore/bootstrap.sql && -f operations/gates/verify_credentials.py ]] || { printf '%s\n' AI_ROOT_REQUIRED >&2; exit 2; }
exec python3 -B operations/gates/verify_credentials.py "$@"

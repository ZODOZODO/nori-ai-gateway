#!/usr/bin/env bash
# 배포 rollback 리허설(Plan P15 L-6). 사용: --exec-sha <40hex> --runconfig <main> --from-digests <A image-digests.json> --to-digests <B>
# 레지스트리 label 조회 자격은 env(AIGW_HARBOR_READ_USER/TOKEN)로만. 본체는 rollback_rehearsal.py.
set -euo pipefail
set +x
export PYTHONDONTWRITEBYTECODE=1
[[ -f ops/postgres/bootstrap.sql && -f ops/gates/rollback_rehearsal.py ]] || { printf '%s\n' AI_ROOT_REQUIRED >&2; exit 2; }
exec python3 -B ops/gates/rollback_rehearsal.py "$@"

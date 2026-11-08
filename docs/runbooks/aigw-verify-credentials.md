# AI Gateway 검증 수용 자격 runbook

대상: namespace `tc` 검증 환경 전용(Plan §11.7·H-2). 실행자 = G-VERIFY-CREDS 운영자, Linux bash, 이미 가진 namespace `tc` 권한만. 스크립트는 `--namespace` 를 받지 않는다. prod values 는 이 Secret 들을 참조하지 않고 `ProductionSafetyGuard` 가 `test-` kid·hook 토큰을 거부한다. 실제 실행은 승인·게이트 뒤에만 한다(현재 NOT_RUN — 로컬 stub selftest 만 수행).

## 다루는 Secret

| Secret | 키 | 쪽 |
|---|---|---|
| `ai-gateway-acceptance-signer` | `signer-private.jwk`(EC P-256, kid `test-<UTC>`) | 수용 |
| `ai-gateway-acceptance-client` | `service-token`, `hook-token` | 수용 |
| `ai-gateway-db-acceptance-ro` | `password`(역할 `aigw_acceptance_ro`, 두 DB release 공유) | 수용 |
| `ai-gateway-acceptance-fixture-writer` | `access-key`, `secret-key`(F3·F4, SeaweedFS 관리자가 만든 값 — 스크립트는 존재만 확인) | 수용 |
| `ai-gateway-actor-jwks` | `jwks.json`(공개 JWK 만) | Gateway |
| `ai-gateway-service-tokens` | `service-tokens.json`(`{"<sha256 hex>":"TC"}`) | Gateway |
| `ai-gateway-test-hooks` | `hook-token-sha256` | Gateway |

값은 스크립트 메모리와 `kubectl apply -f -`·`kubectl exec -i` 의 stdin 으로만 다닌다. 출력·증거·로그에는 Secret 이름·항목 상태·고정 사유 코드만 남는다. 값을 확인하려고 `kubectl get secret -o yaml|json` 을 원문으로 출력하지 않는다.

## 입력(provision·rotate·cleanup)

DB 재배포에 쓰는 env 를 부작용 전에 모두 확인한다(누락 → exit 2, 변경 0).

- 공통: `AIGW_EXEC_CHART_SHA`(승인 nori-tc-exec chart commit), `AIGW_IMAGE_DIGESTS`(image-digests.json 경로).
- 존재하는 DB release 마다: `ai-gateway-db` → `AIGW_RUNCONFIG`, `ai-gateway-db-restore` → `AIGW_RESTORE_RUNCONFIG`.

## 명령

```bash
bash ops/gates/aigw-verify-credentials.sh provision --stage F1   # F1..F4
bash ops/gates/aigw-verify-credentials.sh check --stage F1
bash ops/gates/aigw-verify-credentials.sh rotate --stage F1
bash ops/gates/aigw-verify-credentials.sh cleanup
```

### check

항목(`actor-signer`·`service-token`·`hook-token`·`db-acceptance-ro`, F3·F4 는 `fixture-writer` 추가)마다 stdout 에 `<항목> MATCH|MISSING`, stderr 에 MISSING 사유 1줄을 쓴다. 사유는 `SECRET_ABSENT:<Secret>`·`KID_NOT_TEST_PREFIX`·`JWKS_HAS_PRIVATE_MEMBER`·`PUBLIC_KEY_MISMATCH`·`HASH_MISMATCH`·`FORMAT_INVALID` 중 하나다. 증거는 `evidence/<stage>/gates/verify-credentials-check-<UTC>.json`(items·missingReasons)에 남는다.

- exit 0 = 전부 MATCH, 12 = 하나라도 MISSING.
- Secret 조회 오류는 부재가 아니다 → `SECRET_QUERY_FAILED:<이름들>`·exit 1(증거 미기록).
- check 는 Secret 대조일 뿐 실제 DB 접근 PASS 가 아니다. 접근 경계는 provision/rotate 의 접속 probe 와 수용 하네스의 `OracleQueries.verifyDatabaseBoundary` 가 확인한다.

### provision

1. 부작용 전 판정: DB release(StatefulSet) 조회(조회 오류 = 실패, 둘 다 없음 = `DB_RELEASE_MISSING` exit 2), 입력 env, 존재하는 release 각각 P06 `hba_policy.py live`(실패 → `HBA_LIVE_CHECK_FAILED:<release>`). 하나라도 실패하면 Secret 을 바꾸지 않는다.
2. 수용 Secret 3종(signer·client·db-ro)이 모두 있으면 덮어쓰지 않고 check 만 한다. 일부만 있으면 `PROVISION_PARTIAL_STATE` exit 2(불일치는 rotate 로만 고친다).
3. 새 자격 한 벌을 만든다. 키는 `openssl ecparam -name prime256v1 -genkey` 파이프로 만들고, 토큰·비밀번호는 32바이트 CSPRNG 다. 수용 쪽 Secret 을 먼저, Gateway 쪽 공개키·해시를 나중에 apply 한다.
4. 존재하는 DB release 마다 `aigw-helm.sh --release <db> --mode deploy … --acceptance-role enabled` 로 bootstrap 을 재실행해 역할을 반영한다. 이어서 DB Pod 안 실제 접속 probe(비밀번호는 stdin)가 `ai_gateway` SELECT = OK 이고 `postgres`·`template1` 의 TCP·Unix socket 이 모두 HBA 거부인지 확인한다. 어긋나면 `ACCEPTANCE_ACCESS_BOUNDARY_FAILED:<release>` exit 1. 복원 release 를 자격 때문에 새로 만들지 않는다.
5. 존재하는 앱 Deployment(`ai-gateway`·`ai-gateway-restore`)를 `rollout restart` 하고 status 를 확인한다(Gateway 는 기동 시 1회 로드). 그다음 check 결과로 종료한다.

### rotate

기존 signer·client Secret 이 있어야 한다(없으면 `ROTATE_REQUIRES_EXISTING` exit 2). provision 의 1·3~5 단계와 같고, Gateway 쪽에서는 이전 kid 공개키와 이전 서비스 토큰 해시만 빼고 새 값을 넣는다(그 밖 키·시스템 토큰 보존). DB 자격은 두 release 가 같은 Secret 을 쓰므로 존재하는 양쪽 bootstrap·probe 가 끝난 뒤에만 수용을 재개한다.

### cleanup(검증 종료 P29 뒤·환경 폐기 시)

순서(Plan 승인 A). 한 단계라도 실패하면 그 자리에서 exit ≠0 이고, 뒤 단계(특히 공유 Secret 삭제)는 하지 않는다.

1. 부작용 전: DB release 조회·입력 env·Secret 조회(오류 = 실패)·존재하는 release 각각 P06 HBA live.
2. 수용 Job `ai-gateway-acceptance`·`ai-gateway-restore-acceptance` 삭제(새 수용 연결 차단, 다른 workload 무변경).
3. 존재하는 DB release 마다 P06 admin exec 경로로 역할이 있을 때만 `ALTER ROLE aigw_acceptance_ro NOLOGIN`, 그 역할 세션만 `pg_terminate_backend`.
4. 존재하는 DB release 마다 `--acceptance-role disabled` 재배포. 이어서 `pg_roles` 의 역할 수 0, 기존 자격 재접속이 어느 DB 로도 OK 가 아님을 확인한다. 실패 시 `ACCEPTANCE_ROLE_REMAINS`·`ACCEPTANCE_LOGIN_STILL_ALLOWED`.
5. 수용 Secret 4종(signer·client·db-ro·fixture-writer) 삭제.
6. Gateway 쪽 신뢰 제거: `jwks.json` 에서 `test-` kid 와 수용 kid 를 빼고, `service-tokens.json` 에서 수용 토큰 해시만 뺀다(남는 항목이 없으면 그 Secret 삭제). `ai-gateway-test-hooks` 는 삭제한다.
7. 실행 중 앱 Deployment 마다 `<app> RESTART_OR_UNINSTALL_REQUIRED` 를 출력한다. 제거된 신뢰는 재기동 전까지 Gateway 메모리에 남으므로 운영자가 재배포 또는 uninstall 한다.

이미 없는 release·역할·Secret 은 멱등 성공이다. 같은 명령을 다시 실행해도 결과가 같다.

## 단계 게이트와의 관계

`aigw-stage-gate.sh` 는 배포 전에 `check --stage <행렬의 검증 자격 단계>` 를 부른다(F3-PROBE 는 F2). MISSING 이면 RUN ABORTED(`VERIFY_CREDENTIALS_MISSING`)·exit 12 이고 배포 0 이다. provision·rotate·cleanup 은 게이트가 부르지 않는 운영자 명령이다.

## 로컬 확인

```bash
bash ops/gates/aigw-verify-credentials-selftest.sh   # stub kubectl·aigw-helm·hba_policy, 실제 자격·클러스터·DB 0
```

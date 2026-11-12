# 검증 환경 런타임 자격

Status: NOT_RUN

실제 클러스터 자격의 조회·생성·교체를 이번 구현에서 실행하지 않았습니다. 아래는 namespace tc의 기존 쓰기 권한을 가진 승인 운영자 절차입니다. 권한 부여·Secret 값의 출력·파일 저장은 하지 않습니다.

## Secret `ai-gateway-config`

앱 자격과 DB 설정은 파일 Secret `ai-gateway-config` 하나에 둡니다(Plan U6 ①). 차트는 이 Secret 을 만들지 않고 참조만 하며(`configSecret.create=false`), 앱 Pod 는 그 행에 필요한 키만 `/app/config/<키>` 로 투영해 읽습니다. 운영자가 `kubectl create secret generic ai-gateway-config -n tc --from-file=<키>=<파일> …` 로 만들고 값은 파일·stdin 으로만 넣습니다(명령행 리터럴 0).

| 키 | 내용 | 읽는 쪽 |
|---|---|---|
| `ai-gateway-db.properties` | 레포 `config/ai-gateway-db.properties` 와 같은 placeholder 파일(URL 기본값 = 공유 서버 192.168.0.150:15432/nori_ai_gateway) | 앱 |
| `AI_GATEWAY_DB_USER` | 단일 계정 이름 | 앱 env, 수용 Job env, 격리 시험 인스턴스 bootstrap |
| `AI_GATEWAY_DB_PASSWORD` | 단일 계정 비밀번호 | 앱 env, 수용 Job 비밀번호 파일, 격리 시험 인스턴스(superuser·계정) |
| `token` | GitLab 읽기 토큰 | 앱 |
| `access-key`·`secret-key` | SeaweedFS 읽기 자격(F3·F4 행) | 앱 |
| `service-tokens.json`·`jwks.json`·`hook-token-sha256` | Gateway 쪽 서비스 토큰 해시·actor JWKS·hook 토큰 해시 | 앱(검증 수용 자격 도구가 키 단위로 갱신 — `aigw-verify-credentials.md`) |

계정 값은 `AI_GATEWAY_DB_USER`·`AI_GATEWAY_DB_PASSWORD` 두 키에만 둡니다. `ai-gateway-db.properties` 키에는 계정 값을 쓰지 않고 레포 파일과 같은 placeholder 를 두어, 앱(파일)·수용(env·파일)이 같은 두 키에서 값을 받게 합니다.

DB 역할 Secret 의 provision·회전 명령은 없습니다. 운영 DB 는 빌려 쓰는 공유 서버의 단일 계정이고 계정·비밀번호 관리는 서버 담당자 소관입니다(`aigw-db-server-prerequisites.md`). 비밀번호가 바뀌면 운영자가 두 키를 갱신한 뒤 앱 Deployment 를 재시작합니다(수용 Job 은 실행마다 읽습니다).

## check

`check --stage F1|F2|F3|F4`는 `ai-gateway-config` 의 키 이름 `ai-gateway-db.properties`·`AI_GATEWAY_DB_USER`·`AI_GATEWAY_DB_PASSWORD`·`token` 을 검사합니다. F3/F4는 SeaweedFS `access-key`·`secret-key` 가 추가로 필요합니다. 추론 자격 Secret은 어느 단계에도 없습니다(Plan §11.9). 그 밖 단계 값은 exit 2(`STAGE_INVALID`)입니다. 모든 누락을 표시하고 exit 13을 반환합니다. 증거는 evidence/<stage>/gates/runtime-credentials-check-<UTC+고유값>.json에 이름·필드·PRESENT/MISSING만 기록합니다. 키 이름만 조회하고(go-template) 원시 Secret JSON은 조회하지 않습니다. 존재 검사는 identity 권한·값의 유효성을 증명하지 않습니다. 2026-11-12 철회(seq17)(SPEC §12.4) — 이력: RESTORE 단계, 백업 목적지 Secret `ai-gateway-backup-destination` 의 유형별 필드 검사와 `AIGW_RUNCONFIG` 의 `backup.type` 확인, 복원 release 의 `SPRING_DATASOURCE_URL` 복원 인스턴스 연결, DB 역할 Secret 목록의 backup.

검증 수용 자격(서비스 토큰 해시·actor JWKS·hook 토큰 해시와 acceptance-signer/client)은 Plan §11.7의 별도 G-VERIFY-CREDS 생산자(P15)가 준비합니다. 수용 oracle 의 DB 자격은 앱과 같은 단일 계정이라 별도 DB 역할·Secret 이 없습니다(Plan §11.7 2026-11-10).

GitLab·SeaweedFS는 각 원천 게이트 소유자가 이미 승인한 자격을 등록합니다. 저장소·로그·채팅에 값을 남기지 않습니다. 추론 서버는 Gateway가 빌려 쓰는 공유 자원이며 Gateway는 기존 endpoint(`inference.baseUrl`)를 자격 없이 직접 호출합니다(SPEC §26.1). 추론 자격 Secret·토큰 회전 절차는 없고, 비 2xx(401·403 포함)·파싱 불가 응답은 P19 readiness가 `INFERENCE_UNAVAILABLE`(사유 `ENDPOINT_ERROR`)로 분류합니다. 이번 단계에는 추론 서버 접근을 하지 않습니다.

## 배포와의 관계

`operations/deploy/aigw-helm.sh --mode deploy` 는 부작용 전에 차트 checkout(operations/ 미커밋·미추적 변경 0, 앱 release 는 HEAD = `image-digests.json` 의 `buildIdentity.gitSha`)을 확인하고(실패 exit 2), 이어서 이 check 를 부릅니다(누락 exit 13). 성공 증거 `evidence/F1/gates/deploy-<release>-*.json` 에는 `chartRepoSha`(배포 checkout commit)·`chartDigest`(`helm package` SHA-256)를 남깁니다.

## 로컬 확인

```bash
bash operations/gates/aigw-runtime-credentials-selftest.sh   # stub kubectl·stub Helm upgrade/status, 실제 Helm package/lint/template·git. 실제 Secret·클러스터 0
```

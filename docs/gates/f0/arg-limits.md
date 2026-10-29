# G-ARG-LIMITS — 분류 출력 인자 유효 범위 조사 (F0 측정 전)

- 조사일: 2026-10-29 (P02, 정적 조사 — 실행·외부 API 호출 0)
- 기계 판독본: `eval/f0/arg-limits-v1.json` (이 문서와 필드·상태·근거가 같아야 한다 — `ArgLimitsDocumentConsistencyTest` 가 대조)
- 원칙(SPEC §14.5): 식별자·날짜·검색어의 기존 유효 범위를 줄이지 않는다. 실제 코드·스키마·공식 문서 근거가 확인된 필드만 `CONFIRMED`, 근거가 없으면 임의 상한(예: 64·16)을 두지 않고 `UNRESOLVED` 로 남긴다.
- 결과: **CONFIRMED 2 (`t`, `eqp`) / UNRESOLVED 3 (`from`, `to`, `q`)** → `f0 cases` 는 NORMAL 만 기록하고 MAX·ESCAPE 생성을 거부한다(exit 2, `ARG_LIMITS_UNRESOLVED`). AC-37-OUT-MAX·AC-37-OUT-ESCAPE 는 NOT_RUN 이며 F0 exit 를 막는다(Plan §5 G-ARG-LIMITS).

| field | status | format | maxChars | charUnit | 근거 |
|---|---|---|---|---|---|
| t | CONFIRMED | ENUM:KQ\|LQ\|INC\|GQ\|UNS | 3 | CODEPOINT | SCHEMA `nori-tc-exec/docs/task/ai-rag-gateway-architecture.md:1203` (SPEC §14.5 압축 스키마 enum) |
| eqp | CONFIRMED | TC_EQP_ID | 64 | CODEPOINT | CODE `nori-tc-common/db/jpa/tc-db-jpa-common-schema/src/main/java/com/nori/tc/db/jpa/common/entity/eqp/TcEqpEntity.java:56`, SCHEMA `nori-tc-exec/db/migration/V1__baseline.sql:420`, CODE `nori-tc-ui-backend/ui/adapter/tc-ui-web-adapter/src/main/java/com/nori/tc/ui/adapters/web/dto/request/EqpCreateRequest.java:33` |
| from | UNRESOLVED | — | — | — | 근거 없음(`TIME_FORMAT_CONTRACT_NOT_IMPLEMENTED`) |
| to | UNRESOLVED | — | — | — | 근거 없음(`TIME_FORMAT_CONTRACT_NOT_IMPLEMENTED`) |
| q | UNRESOLVED | — | — | — | 근거 없음(`SOURCE_INPUT_LIMIT_UNVERIFIED`) |

## eqp (CONFIRMED, 64 코드포인트)

- `TcEqpEntity.java:56` — `@Column(name = "eqp_id", length = 64, nullable = false, unique = true)`.
- `V1__baseline.sql:420` — `tc_eqp.eqp_id VARCHAR(64) NOT NULL` (PostgreSQL `varchar(n)` 의 n 은 바이트가 아니라 문자 수이므로 단위 = 코드포인트).
- `EqpCreateRequest.java:33` — `eqpId` 는 `@NotBlank` 만 있고 문자 집합·패턴 제한이 없다. 따라서 MAX 표본은 한글·숫자·ASCII 혼합 64자 ID 를 쓴다(fixture `eval/golden/tc-registry-fixture-v1.json` 에 같은 ID 를 등록 리소스로 둠, 자르지 않음).
- 참고(근거로 쓰지 않음): 같은 SQL 의 `tc_deploy_target_plan.eqp_id VARCHAR(100)`(:897)은 배포 대상 계획 테이블이며 EQP 리소스 정본이 아니다. 더 넓은 값이 EQP ID 로 존재할 수 없으므로 리소스 상한은 `tc_eqp` 64 다.

## from / to (UNRESOLVED)

Plan 은 "분류 스키마가 받아들이는 시각 형식 전체(초·소수초·offset 허용 여부)를 `TimeWindowResolver` 입력 형식과 Context `timeWindow` 형식에서 확인한 최장 형태" 를 요구한다. 조사 결과:

- `TimeWindowResolver` 는 Plan P13 구현 예정이며 워크스페이스에 코드가 없다(파일 검색 0건).
- SPEC §14.5(:1203)는 `"2026-10-03T00:00"` 형태의 **예시**만 있고 초·소수초·offset 허용 여부를 정의하지 않는다.
- SPEC §22.2(:1666)는 Context `timeWindow{from,to}` 를 TC Backend 가 "형식·순서·inputLimits 로 검증" 한다고만 적고 형식 문법을 정의하지 않는다.
- 따라서 최장 형태를 근거로 확정할 수 없다. 예시 길이(16자)로 일률 고정하는 것은 Plan 이 금지한다.
- 해소 경로(결정 필요): SPEC 소유자가 Context `timeWindow`·분류 출력 시각 형식(초·소수초·offset 허용 여부)을 정하거나, P13 `TimeWindowResolver` 입력 계약이 먼저 확정돼야 한다(`[SPEC]`).

## q (UNRESOLVED)

`q` 를 받는 실제 Source 입력은 Loki 줄 필터(`tc.logs.search`), GitLab `search` 파라미터(`tc.git.searchCode`), Knowledge keyword(`tc.knowledge.search`)다.

- Loki: 이 조사에서 LogQL 줄 필터 문자열 길이의 공식 문서 상한을 확인하지 못했다(공식 문서 미확인 — 추정 상한을 두지 않음).
- GitLab Search API: 이 세션은 외부 문서 열람을 하지 않았으므로 `search` 파라미터 상한의 공식 문서·tagged source 근거를 확인하지 못했다.
- Knowledge keyword: Plan P23 구현 예정이며 코드가 없다.
- 해소 경로: 사용하는 Loki·GitLab 버전의 공식 문서(또는 tagged source)에서 상한을 확인해 `API_DOC`/`CODE` 근거로 등록하거나, 근거가 없으면 SPEC 소유자가 상한을 정한다(`[SPEC]`). 여러 Source 가 같은 `q` 를 받으면 가장 좁은 근거 상한이 아니라, 각 Source 가 받아들이는 범위를 줄이지 않는 값을 SPEC 이 정해야 한다.

## 결과 사용처

- 이 파일이 P05 Manifest `inputLimits`·P20 분류 스키마의 유일한 입력이다(그보다 좁게 설정하면 P05 `InputLimitsNotNarrowerTest` FAIL).
- `F0RunnerCli f0 cases` 는 `UNRESOLVED` 가 있으면 MAX·ESCAPE 를 만들지 않고 `eval/f0/output-boundary-cases.blocked.json` 에 차단 사유를 남긴다.

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

## v2 승인 계약 (2026-10-30)

위 본문·표는 2026-10-29 v1 조사 이력이다. `eval/f0/arg-limits-v1.json`·v1 PREREG·측정 결과·실패 증거를 수정하거나 PASS로 바꾸지 않는다. 아래는 사용자 승인으로 확정한 별도 v2 계약이며 기계본은 `eval/f0/v2/arg-limits-v2.json`이다. 조사 이력의 `UNRESOLVED`를 Source 공식 상한 발견으로 소급 변경하지 않는다.

| field | status | format | maxChars | charUnit | 근거 |
|---|---|---|---|---|---|
| t | CONFIRMED | ENUM:KQ\|LQ\|INC\|GQ\|UNS | 3 | CODEPOINT | SCHEMA `nori-tc-exec/docs/task/ai-rag-gateway-architecture.md:1203` |
| eqp | CONFIRMED | TC_EQP_ID | 64 | CODEPOINT | CODE `nori-tc-common/db/jpa/tc-db-jpa-common-schema/src/main/java/com/nori/tc/db/jpa/common/entity/eqp/TcEqpEntity.java:56`, SCHEMA `nori-tc-exec/db/migration/V1__baseline.sql:420`, CODE `nori-tc-ui-backend/ui/adapter/tc-ui-web-adapter/src/main/java/com/nori/tc/ui/adapters/web/dto/request/EqpCreateRequest.java:33` |
| from | CONFIRMED | DATETIME:uuuu-MM-dd'T'HH:mm:ss.SSSSSSSSSxxx | 35 | CODEPOINT | SCHEMA `nori-tc-exec/docs/task/ai-rag-gateway-architecture.md:1207` |
| to | CONFIRMED | DATETIME:uuuu-MM-dd'T'HH:mm:ss.SSSSSSSSSxxx | 35 | CODEPOINT | SCHEMA `nori-tc-exec/docs/task/ai-rag-gateway-architecture.md:1207` |
| q | CONFIRMED | TEXT | 256 | CODEPOINT | SCHEMA `nori-tc-exec/docs/task/ai-rag-gateway-architecture.md:1208` |

- **근거의 의미:** 시간 문법과 q256은 2026-10-30 사용자가 승인한 Gateway 제품 계약이다. 특히 q256은 Loki·GitLab·Knowledge의 공통 공식 상한이 아니다. 더 긴 검색어가 Source에서 유효하더라도 Gateway에서는 지원하지 않는다는 범위 축소를 명시적으로 승인받았다. eqp64의 기존 TC 리소스 근거는 유지한다.
- **시간 허용 범위:** 실제 허용 문법은 `uuuu-MM-dd'T'HH:mm[:ss[.fraction]][offset]`이며 초 선택·소수초 1~9자리·선택 `Z`/`±HH:mm`를 지원한다. `DATETIME:…xxx`는 경계 생성용 최장 표기이지, 모든 입력에 초·9자리 소수초·offset을 강제하는 문법이 아니다. 실제 달력 날짜·시각·offset을 엄격 검증하고, offset 생략은 Context 시간대의 local time, 명시 offset은 같은 instant를 Context 시간대로 변환한다. 나노초·초 정밀도를 유지하고 밀리초로 내리지 않는다. 분 단위 기존 표기는 유지한다.
- **원문 추출·실패:** 일반 `"원문"`은 내부 raw substring으로 보존하며 역슬래시를 해석하지 않는다(Windows 경로 `C:\temp`의 `\t`를 TAB으로 바꾸지 않음). 명시 `json:"…"`에서만 JSON 문자열 escape를 엄격히 한 번 디코드한다. 내부 quote/backslash/control는 이 명시 표기로 모호함 없이 표현할 수 있다. 질문을 한 번 스캔해 겹치지 않는 후보를 출현 순서의 Q1…에 바인딩하고 동일값 중복도 occurrence별 ID를 유지한다. 빈 원문·닫힘 누락·잘못된 명시 JSON·짝 없는 surrogate는 분류 전 기존 `FAILED`/`PLAN_INVALID`와 `clarification{reason, fields:["q"]}`로 거부한다. 문자 수는 추출된 실제 원문의 Unicode 코드포인트다. 후보가 하나라도 256자를 넘으면 절삭·대체 없이 기존 `FAILED`/`INPUT_BUDGET_EXCEEDED`와 `clarification{reason, fields:["q"]}`로 거부한다. 두 사전 거부는 분류·Source·Composer0이며 새 제품 errorCode를 만들지 않는다.
- **LLM wire·원문 복원:** 모델 입력은 전체 질문과 `qRefCatalog=["Q1","Q2",…]` 참조 ID 목록이며 원문 value를 카탈로그에 중복 복사하지 않는다. 실제 ID→원문 매핑은 요청 메모리만 사용한다. B wire는 `{t,eqp,from,to,qRef}`, A는 여기에 steps를 더한다. 후보0이면 qRef=null만, 후보가 있으면 실제 참조 하나만 허용한다. 원문 q 키·위조/미존재 참조·부적절한 null은 스키마 실패이며 같은 요청 바인딩으로 1회 재시도 후 기존 PLAN_INVALID/clarification(q)로 종결한다. 유효 참조 검증 후 기존 resolvedTask.q에 원문을 그대로 복원한다. q256은 resolved 원문·Tool 경계이며 qRef 길이 제한이 아니다. 참조 검증·복원 전 Source·Composer0, 교차 요청·이전 턴 바인딩 재사용0, 원문 trim·정규화·절삭0이다. Source별 인가·scope·캐시 정책과 기존 원문 비노출을 유지한다.
- **후속 책임:** P05가 공통 inputLimits와 Manifest를 이 기계본에서 생성하고, P13이 Context·직전 턴·추출 시간의 실제 의미와 나노초 보존 및 Tool 검색어 경계를 검증하며, P20이 입력 q 경계·분류 스키마·실패 매핑을 적용한다. 현재 문서 확정은 미래 P05/P13/P20 구현 완료를 뜻하지 않는다.
- **F0 상태:** v2 필드 계약은 모두 CONFIRMED이지만 등록 출력 상한(v2 분류 192) PASS가 아니다. 최장35자 시각·q256·eqp64와 escape 원문 입력은 유지하고 경계 행의 expectedOutput(qRef)·expectedResolvedOutput(q)를 분리한다. 공식/GGUF 계수 대상은 wire expectedJson이며 실제 생성의 argsExact는 유효 참조와 복원 후 원문 코드포인트 정확 일치를 모두 요구한다. v2 ESCAPE 질문은 `json:` + JSON 문자열 직렬화(q)를 쓴다. 입력은 질문·후보 ID 목록을 포함한 실제 최종 메시지를 계수한다. 출력 상한은 SPEC §14.5 2026-11-02 v2 profile(B192/A256·입력 3,072, v1 B128/A160 은 이력)이고 입력+출력4096·기존 지연/정확도 임계는 바꾸지 않는다. 그래도 초과하면 인자 절삭 없이 예산 조정안을 별도 승인받는다. 기존 v1 PREREG·C1/C2·측정/실패 결과와 57/53 보정 예시는 불변이다. 이번 qRef 입력·출력·하네스 변경은 measurementAffecting=true이며 새 사전등록과 실제 재측정 없이는 v2 PASS를 만들지 않는다. 현재는 사전등록 전 준비이며 P03 미완료·F0 FAIL·P04 미착수다.

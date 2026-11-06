# Verify 시험 훅 계약 v1

P09 CODE/LOCAL_TEST 계약입니다. 공식 사용자 API는 P10, TC Mock 위임·시나리오는 P11, 실제 workflow는 P13, 운영 안전 검사는 P15 책임입니다.

모든 아래 경로는 `verify` 프로필과 `ai.gateway.test-hooks.enabled=true`에서만 등록됩니다. 필수 `ai.gateway.test-hooks.token-sha256-file`은 UTF-8 소문자 SHA-256 64자리(끝 LF 선택) 파일입니다. 부재·형식 오류는 입력 원인·내용을 노출하지 않고 기동 실패합니다. 요청의 `X-AIGW-Hook-Token`을 SHA-256으로 바꿔 constant-time 비교하며 누락·불일치·중복 헤더는 빈 404입니다.

`TestHookAuthFilter`는 raw Filter 빈이나 `@Component`가 아니며 단일 `FilterRegistrationBean`의 `/__test/*` REQUEST 등록만 사용합니다. 서비스 토큰·actor 검증은 `/v1/*`만 담당합니다. `/actuator/health/liveness`, `/actuator/health/readiness`는 둘 밖이며 상태 문자열만 반환합니다.

| 경로 | 입력·응답 |
|---|---|
| POST `/__test/arm` | `{actorSub, requestKey, scenario:{taskType, pauseAt?:[point], haltAt?:point, failSanitizer?:boolean}}` → 204, 중복 409 |
| POST `/__test/reset` | 비 terminal 0일 때만 204, 아니면 409 `{error:"ACTIVE_JOBS"}` |
| GET `/__test/clock` | `{now:UTC ISO8601, mode:SYSTEM 또는 FROZEN}` |
| POST `/__test/clock` | `{op:FREEZE_AT,at}` 또는 `{op:ADVANCE,seconds:양의 정수}` 또는 `{op:RESUME_SYSTEM}` → 현재 시계 |
| POST `/__test/pause` | `{point,op:HOLD 또는 RELEASE,jobId?:ID}` → 204, jobId 생략 시 전역 `*` |
| POST `/__test/sweep-now` | 시간 한도 sweeper 1회 → 204 |

`taskType`은 기존 닫힌 TaskType enum입니다. 정지 지점은 `ADMISSION_FAST_MISS`, `ADMISSION_AFTER_COMMIT`, `BEFORE_CLASSIFICATION`, `TOOL_IN_FLIGHT`, `BEFORE_COMPOSER`, `TERMINAL_PRECOMMIT`, `TERMINAL_POSTCOMMIT`입니다. P09 제품 실행은 접수·분류 전·종결 지점에 연결하고 Tool·Composer 실행 지점은 시험 fake/P13·P20에서 연결합니다. 전역 보류 지점은 `DISPATCH_HOLD`, `SWEEPER_HOLD`, `EXPIRY_CANCELLER_HOLD`, `PURGE_HOLD`이며 뒤의 두 실행자는 P14입니다. `haltAt`의 P09 실제 연결은 분류 전과 terminal 커밋 전·후이며 exit 137을 일으킵니다.

`requestKey`는 접수 요청의 기존 `Idempotency-Key`입니다. `(actorSub,requestKey)` 무장은 60초 절대 TTL이며 동일 키 재무장은 409입니다. 신규 Job INSERT 뒤 같은 접수 tx에서 commit 전에 jobId 결속을 예약합니다. 첫 워커는 afterCommit 깨우기보다 먼저 폴링으로 시작해도 결속을 봅니다. rollback은 결속·정지점을 제거하고 무장을 복원하며 commit은 소비를 확정합니다. 중복 202와 충돌 409는 결속을 호출하지 않습니다. 결속은 해당 Job만 대상으로 하며 다른 actor·Job으로 전파되지 않습니다.

reset은 무장·결속·정지점과 시계를 SYSTEM으로 초기화합니다. 아직 커밋 대기 중인 접수가 있으면 결속 보호를 위해 같은 409를 반환합니다. 위임의 재발급·연장·폐기는 수행하지 않습니다. `FREEZE_AT`의 임의 시각은 reset 직후 비 terminal 0일 때만 허용하며 이후에는 뒤로 이동할 수 없습니다. 초기 `ai.gateway.test-hooks.clock.{initial,mode}`는 Clock 생성 시 적용하므로 Router 이전에 확정됩니다. 무장·결속·시계는 프로세스 메모리이며 재시작하면 사라집니다.

SQL·URL·호스트·파일 경로 입력이나 토큰·위임·원문·Prompt·답변·벡터 반환은 허용하지 않습니다. 시험용 `/v1/query`, 결과 GET, 하네스 관측 endpoint와 P09 fake 포트는 integrationTest 산출물에만 있고 제품 런타임에 없습니다. 프로세스 시험의 재개 분류 입력은 시험 config가 명시 재주입하며 제품의 hook 상태 내구성을 주장하지 않습니다. 실제 Source·LLM·운영 수용 증거로 사용하지 않습니다.

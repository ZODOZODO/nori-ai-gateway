# AI Gateway API v1

이 문서는 승인된 Spec §9.1·§9.3·§12.2·§15.2·§15.3·§22.3과 Plan P10의 HTTP 계약을 설명합니다. 로컬 구현·시험은 운영 배포·수용 완료를 의미하지 않습니다.

모든 `/v1` 요청은 서비스 Bearer 토큰과 매 요청 새 actor 단언을 검증합니다. `X-Actor-Assertion` 또는 body `contextRef` 중 하나만 사용합니다. `dlg`는 `/v1/query`, `/v1/analyses`, `/v1/conversations/{id}/messages`의 POST에만 필요하며 다른 경로에는 넣지 않습니다. owner·actor는 검증된 단언과 서비스 바인딩에서 정하고 body로 선택하지 않습니다.

| 경로 | 성공 응답 |
|---|---|
| `POST /v1/conversations` | 201, 새 대화의 `id`, 소유 범위, 생성·수정·만료 시각, 상태 |
| `GET /v1/conversations/{id}` | 200, `conversation`과 `messages` |
| `POST /v1/query` | 202, `jobId`, `conversationId`, `messageId`, `queuePosition` |
| `POST /v1/conversations/{id}/messages` | 같은 202 형식, 경로의 대화에 후속 질문 접수 |
| `POST /v1/analyses` | 같은 202 형식, 대화가 없으면 접수와 원자 생성 |
| `GET /v1/analyses/{jobId}` | 200, Job 상태와 접근 가능한 완성 결과 |
| `POST /v1/analyses/{jobId}/cancel` | 204, 본인·boundSystem의 취소 가능한 Job |
| `GET /v1/analyses/{jobId}/events` | `text/event-stream`, 저장 진행 이벤트와 heartbeat |
| `GET /v1/analyses/{jobId}/evidence/{evidenceId}/source` | 200, 이용 가능한 원문의 `maskedText` |
| `GET /v1/analyses/{jobId}/blocks/{blockId}/export` | 200, 저장 결과에서 식별한 블록 |
| `GET /v1/audit/jobs/{jobId}` | 200, 본인·boundSystem Job의 보존 기간 내 감사 |

접수 body는 `query`, 선택 `conversationId`, `context`, `resourceRefs`, `options`를 받습니다. 알려진 실행 입력만 snapshot에 보존하며 JWS·contextRef·dlg·HTTP header·클라이언트 신원은 포함하지 않습니다. `context.timezone`, `context.referenceTime`, 선택 `context.timeWindow{from,to}`는 기존 날짜 문법과 timezone·offset·나노초를 검증하고 원래 문자열을 보존합니다. `resourceRefs`의 각 항목은 `{type,ref}`이며 기존 `ResourceRef`로 실행 입력에 투영합니다. `options.allowTools/allowKnowledge`는 부재·null·boolean을 구분하고 미제공을 기본 true/false로 바꾸지 않습니다. 알려진 선택 필드의 부재·명시 null·값을 Job마다 독립적으로 보존합니다. `sourceVersions`는 새 wire 계약으로 추가하지 않습니다.

`Idempotency-Key`의 범위는 owner·actor입니다. 같은 키·같은 정규화 입력은 기존 `jobId`와 저장된 같은 `messageId`로 202를 반환하고 snapshot·위임·TTL을 갱신하지 않습니다. 질문·대화·경로 또는 `context/resourceRefs/options`의 부재·null·값이 달라지면 409 `{"error":"IDEMPOTENCY_KEY_CONFLICT"}`입니다. 준비 미완료·큐 포화는 503과 `Retry-After`를 반환하고 신규 Message·Job·대화를 남기지 않습니다. 접수 후 실패는 GET의 HTTP 200과 `jobStatus`·결과 상태로 조회합니다.

정상 결과 응답은 `jobId`, `jobStatus`, `createdAt`, `terminalAt`, `result`를 포함합니다. `result`는 `status`, `errorCode`, 저장 `payload`, `clarification`을 담으며 실행 중 결과는 null입니다. 현재 단언의 권한을 Source 안에서는 ANY, Source 사이에서는 AND로 판정하고 참조 문서는 모두 현재 `AVAILABLE`이어야 합니다. 실패·UNKNOWN·잘못된 의존 계약은 fail-closed입니다. 의존성 없는 P09 종결의 정확한 `{}`는 빈 의존으로 호환합니다. 결과 접근 거부는 아래 다섯 필드만 반환합니다.

```json
{"jobId":"...","jobStatus":"SUCCEEDED","createdAt":"...","terminalAt":"...","resultAccess":"DENIED"}
```

같은 평가를 결과 GET·각 결과 메시지의 대화 이력·Evidence 원문·블록 export·감사 조회에 적용합니다. 거부 시 Source·권한 코드·문서·개수·사유·답변 일부를 노출하지 않고 Tool·LLM 재호출이나 재생성을 하지 않습니다. 원문 참조의 owner·sourceRef·chunkId·expectedRevision은 저장 Evidence에서 읽으며 URL·query에서 revision을 받지 않습니다. `EvidenceSourceReader`가 비가용이면 미존재 Evidence와 같은 빈 404입니다. P10의 Knowledge 상태/원문 리더는 P23 합류 전 fail-closed 구현 하나씩이며, Knowledge 참조에 UNKNOWN/Unavailable을 반환합니다. 과거 revision을 별도 보관하거나 새 revision 본문으로 옛 근거를 대체하지 않습니다.

타 owner·타 actor·부재·만료 접근은 모두 HTTP 404, 빈 body입니다. 만료는 Gateway Clock의 `now >= conversation.expiresAt`이며 조회 응답 바로 전과 SSE의 이벤트/heartbeat 쓰기마다 동기 guard를 검사합니다. 열린 SSE의 예약 종료는 보조 수단입니다. P13의 Tool·캐시, P20의 Prompt·LLM은 같은 `ExpiryCheckpoint`를 소비하는 후속 단계입니다.

SSE 연결·재연결은 매 요청 새 actor 단언을 사용합니다. `Last-Event-ID`는 같은 Job·owner·actor의 실제 저장 이벤트 ID일 때만 유효하며, 다르면 스트림을 시작하지 않고 빈 404를 반환합니다. 이후 ID 순으로 재생하며 `external_suppressed` 이벤트는 제외합니다. 전송 data의 허용 키는 `type,jobId,seq,queuePosition,toolId,status,jobStatus,errorCode`이고 답변 본문은 전송하지 않습니다. heartbeat 안전 초기값은 15초이며 `ai.gateway.sse.heartbeat`로 설정합니다. 열린 스트림의 이벤트별 TC 재인가는 F5b 중계 책임입니다.

`job.started`는 실제 dispatcher의 `QUEUED → RUNNING` 전이와 같은 트랜잭션에서 저장합니다. commit 뒤 같은 Job의 queued, started를 발행한 다음 worker를 실행합니다. 시작 전 종결이 먼저 이기면 started 행·발행·worker 실행이 없으며 rollback도 RUNNING과 started를 함께 취소합니다.

최종 결과 wrapper의 `payload.blocks[]`에는 서버가 확정한 배열 순서에 따른 additive `id`(`b0`, `b1`, …)가 있습니다. 전체 참조는 `(jobId,id)`이며 같은 dataRef를 가진 블록도 구분합니다. content 안의 업무 id는 유지하며 upstream `ResultBlock`·Tool envelope·Manifest에는 이 필드를 요구하지 않습니다. 최종 저장 직전에 `ResultBlockIds`를 사용하는 실제 workflow producer는 P13의 책임입니다. GET·대화 이력은 저장 id를 재발급하지 않고, Export는 해당 Job의 저장 id가 정확히 한 건일 때만 같은 블록을 반환합니다. 미존재·중복 id는 빈 404입니다. P10의 helper 기반 저장 fixture 시험은 P13 실제 workflow 생산/저장 검증을 대신하지 않습니다.

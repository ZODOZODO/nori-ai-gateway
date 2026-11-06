# TC AI READ Mock 계약 1.0

이 문서는 승인 Spec §7.6·§9.3 및 Plan P11의 F1 Mock typed 계약입니다. 실제 TC 연결·권한 등록·운영 적용 증거가 아니며, 외부 HTTP endpoint 경로명은 F5a 상세 Spec에서 확정합니다. 이번 산출물은 CONTRACT_READY 후보이고 API 종결·Spring wiring·hook·자식 JVM 복구 검증은 포함하지 않습니다.

## 인가

기존 core `TcSourceAuthorizationPort.authorize(DelegatedCall(sub, dlg), Set<String>)`는 `Allowed(allowed, pdg) | DelegationInvalid | Unavailable`을 유지합니다. `DelegatedCall`은 기존 core의 nested record입니다.

Mock의 `MockTcSourceAuthorizationAdapter.AuthorizationResponse` wire는 네 필드를 모두 요구합니다.

```json
{"allowed":["EQP_READ"],"denied":["AI_LOG_READ"],"pdg":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","evaluatedAt":"2026-11-06T00:00:00Z"}
```

`SchemaContracts`로 직렬화→역직렬화→schema 검증 후, permission 형식 `[A-Z0-9_]{1,64}`, 각 배열의 요청 범위 포함 여부, 배열 내 중복·배열 사이 모순, 소문자 64자리 SHA-256, 유효 `Instant`를 검사하여 core 두 필드로 투영합니다. `pdg`는 반환값을 보존하고 Gateway가 재계산·보정하지 않습니다. `evaluatedAt`에 새 TTL·신선도·시계차 정책은 없습니다. 검증 실패는 `Unavailable`이며 Source 호출은 없습니다. `denied`는 허용 집합에 합치지 않습니다.

빈 요청은 위임의 유효성만 검사합니다. 유효 위임은 wire 양쪽 배열이 빈 상태와 유효 pdg/time을 검증한 뒤 core 빈 `Allowed`로 투영합니다. Source 권한을 허용하지 않습니다. 미존재·폐기·만료·actor 불일치·로그아웃 시나리오는 `DelegationInvalid`, 위임 저장소·권한 catalog·인가 API 장애는 `Unavailable`입니다. 향후 외부 wire 오류의 HTTP 분류는 각각 `DELEGATION_INVALID`와 503입니다.

`SourcePermissionMap`은 `config/source-permissions/tc-v1.json`을 읽습니다. 기존 Source 주소를 그대로 키로 사용하며 미매핑 Source는 거부합니다. `SourceAuthorizationStep.authorizeBefore(sourceId, scope, call)`은 TC scope/actor를 확인한 뒤 Source 권한을 매번 조회하고 TC 기존 ANY-of 조건으로 `Granted(allowed,pdg) | Denied | SourceUnavailable`을 반환합니다. 인가 결과 캐시는 없습니다. 연결 예외·잘못된 pdg는 `SourceUnavailable`입니다.

`AI_WORKLOT_READ`, `AI_EVENT_READ`, `AI_LOG_READ`, `AI_TRACE_READ`, `AI_CODE_READ`, `AI_DOC_READ`는 `mockOnly:true`입니다. 실제 등록 증거가 아닙니다. 현재 `tc.event.getRecent` Manifest의 `requiredPermission=[]`는 기존 파일 그대로이며, Mock business 호출은 별도로 `AI_EVENT_READ`를 재인가합니다. Manifest/Registry 노출 정합은 actual join/P13 소비 검토 항목입니다.

## 업무 조회 typed 경계

`TcBusinessReadPort`의 모든 메서드는 첫 인자로 위 `DelegatedCall`을 요구합니다. 반환은 `TcReadResult<T>(status, observedAt, data, missingFields, warnings, retentionMeta)`이며 실패를 성공·빈 목록으로 바꾸지 않습니다. `RetentionMeta`는 `missingRanges`와 `unverifiedRanges`를 분리합니다.

| 메서드 | 추가 인자 | 자료 타입 | Mock 재인가 |
|---|---|---|---|
| getEqpSnapshot | eqpId | EqpSnapshot | EQP_READ |
| getActiveWorkLots | eqpId | ActiveWorkLots | AI_WORKLOT_READ |
| getRecentEvents | eqpId, TimeWindow | List of RecentEvent | AI_EVENT_READ |
| listDlq | sourceId(gateway/business), TimeWindow | DlqData | DLQ_READ |
| getCommLogs | eqpId, sourceId(gateway/business), TimeWindow | CommLogData | COMM_LOG_READ |
| readModel | Optional modelId | List of ModelNode | MODEL_READ |
| resolveResource | type(EQP/MODEL), ref | List of Resource | EQP_READ/MODEL_READ |

위 타입은 모두 `TcBusinessReadPort`의 nested public record/enum입니다. EQP 네 상태와 조회 관측 시각, 활성 Work/PJ/LOT, 모델 목록·노드 계층을 보존합니다. MULTI·UNKNOWN·ENDED는 임의 currentLot을 선택하지 않습니다. 최근 이벤트·통신 로그·DLQ 항목은 반개구간으로 필터링하고 키·payload 원문을 반환하지 않습니다. 상태 전이·모델의 실제 외부 업무 필드 확정은 F5a의 책임입니다.

Mock 응답은 contractVersion=1.0 wrapper와 typed result를 JSON으로 왕복하고 schema를 검증합니다. 기존 Manifest가 있는 EQP·최근 이벤트·DLQ·모델은 그 실제 outputSchema의 `items` 투영도 검증하고, `schemaDigest(capabilityId)`는 정본 input/output 쌍의 `SchemaContracts.digest`를 공유합니다. version=2.0 fixture는 `FAILED`입니다. Tool envelope/종결 조립은 이번 단계에 구현하지 않습니다.

## 보존·완전성

`SourceRetentionReader.find(sourceId)`는 `Optional<VerifiedOldest(verifiedOldestAt, probedAt)>`를 반환합니다. DB 어댑터·탐침 스케줄러는 P14입니다.

`TimeWindow(startAt,endAt)`는 start<end인 반개구간입니다. `SourceRetentionAttributes`의 `Configured`, `VerifiedOldest`, `Unverified`, `TimeAndCountTrim`을 `RetentionClassifier.classify(window,attrs,now)`에 전달합니다. 결과는 `OutOfRetention(missingRanges)`, `Full(queryWindow,warnings)`, `Partial(queryWindow,missingRanges,warnings)`입니다. end==boundary는 전체 밖, start==boundary는 전체 안입니다. 미확정 속성은 원래 창과 `RETENTION_UNVERIFIED`를 반환합니다.

`TrimCompletenessEvaluator`는 키마다 count/maxEntries/oldestRetained/retentionHours/적용 확인·다른 삭제 경로 배제/조회 잘림을 입력으로 받습니다. 상한 도달 구간은 `unverifiedRanges`이고 확정 누락이나 삭제 경계로 바꾸지 않습니다. 조회 잘림은 `missingRanges`, 키 한쪽 실패는 해당 Source `SOURCE_UNAVAILABLE`·집계 `PARTIAL`입니다.

`DlqCompletenessEvaluator`는 `scanSucceeded/scannedKeys/loadedEntries/decodeFailures`를 받습니다. SCAN 실패는 `SOURCE_UNAVAILABLE`, decode 손실은 실패 건수만 담은 `missingFields`와 `PARTIAL`입니다. 정상 EMPTY/HIT도 `RETENTION_UNVERIFIED`를 유지합니다.

## Mock 상태와 수명

`MockDelegationRegistry(Clock, optional stateDirectory)`는 원문 dlg를 보관하지 않고 SHA-256 키와 `{sub,issuedAt,expiresAt,revoked}`만 메모리·파일에 저장합니다. 유효기간은 발급 시각+300초 절대값이며 정각부터 무효입니다. 같은 해시 재등록은 DUPLICATE이고 갱신·연장하지 않습니다. `revoke(dlgSha256)`는 폐기 상태를 보존합니다.

stateDirectory를 지정하면 `delegations.json`과 시나리오 ID만 담은 `scenario.json`을 force 후 원자 교체하고 생성자에서 읽습니다. 저장 실패·손상 위임 상태는 사용 불가로 처리합니다. 단위시험은 재생성 시 expiresAt/폐기 보존과 원문 부재를 검사합니다. 실제 프로세스 halt/restart·RecoveryModeRouter 선행 loading·verify 전용 state-dir guard·hook 등록은 P10 합류 후 P11 전체 검증 대상입니다.

시나리오는 `tcmock/scenarios/index.json`에 등록된 ID만 선택할 수 있습니다. Mock 호출 계수는 operation 이름별 건수만 보존합니다. 원문 dlg·토큰·payload는 계수와 상태 파일에 포함하지 않습니다.

## P13 설비 범위 DLQ 소비와 F5a 인계

기존 `listDlq(call, sourceId, window)`와 반환 `TcReadResult<DlqData>`·Manifest output shape·contractVersion 1.0은 유지합니다. P13 실제 업무 Tool은 additive `listDlq(call, eqpId, sourceId, window)`를 사용합니다. 기본 구현은 `UnsupportedOperationException`으로 닫으며 기존 source-wide 메서드에 위임해 설비 범위를 약화하지 않습니다. 실제 어댑터는 원천 항목의 검증된 설비 귀속과 `[startAt,endAt)`를 모두 검사한 뒤 기존 DLQ 항목 DTO로 투영해야 합니다.

Mock은 동일 snapshot의 설비 귀속 자료를 먼저 필터링하고 기존 DTO·wire/schema 검증으로 반환합니다. 빈·null·64자를 넘는 eqpId 또는 null 창은 잘못된 입력이고, 설비 귀속 자료 손상은 계약 실패이며, 미등록 Source는 `SOURCE_UNAVAILABLE`입니다. 다른 설비 항목이나 endAt 경계 항목을 합치지 않고, 성공한 필터의 0건은 `EMPTY`로 보존합니다. F5a 실제 TC 어댑터도 원천 설비 귀속을 확인하는 4arg 구현이 필요하며 외부 endpoint·인자 wire의 확정은 F5a 책임입니다. 이 내부 additive 호출을 외부 API 버전 변경이나 실제 TC 연결 증거로 해석하지 않습니다.

실제 workflow의 Source 인가는 각 READ의 현재 허용 집합과 응답 pdg를 소비합니다. 접수 단언의 전체 권한 pdg와 현재 Source 응답 pdg가 같다고 가정하지 않으며, Gateway가 응답 pdg를 계산·보정하지 않습니다. 기존 legacy `PlanValidator.validate`의 검사 계약은 보존하고 actual workflow의 typed prepare·단계 시도·현재 grant 경로로 연결합니다. 인가 응답을 단계 사이에 캐시하지 않습니다.

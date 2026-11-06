# 인입 인증 v1

상태: P08 CODE / 로컬 검증 후보입니다. 운영 배포·실제 Source·P09 JobAdmission·P10 공식 controller·P16/P29 수용 완료를 뜻하지 않습니다. 정본 Spec §9.1·§9.2·§12·§15.1과 승인 Plan P08이 우선합니다.

## 요청과 검증 순서

서비스 Filter는 이름 `serviceTokenAuthFilter`, Servlet URL `/v1/*`, dispatcher `REQUEST`, 최우선 순서로 한 번만 등록합니다. actuator와 `/__test/**`에는 적용하지 않습니다. 서버가 매 요청에 UUID를 발급하며 클라이언트 요청 식별자를 인증·감사 식별자로 채택하지 않습니다.

`Authorization: Bearer <서비스 토큰>`의 SHA-256을 기동 시 고정한 토큰 해시와 `MessageDigest.isEqual`로 비교해 단일 owner를 확정합니다. actor 단언은 JSON Body의 `contextRef`로 전달합니다. Body가 없는 조회 등은 `X-Actor-Assertion` 헤더로 같은 JWS를 전달합니다. 두 전달 위치를 동시에 사용하거나 인증 헤더를 중복 전송할 수 없습니다. 이 헤더는 서명된 단언의 전달 위치이며 신뢰 키·사용자 신원의 근거가 아닙니다.

ES256 허용목록 → 기동 때 로드한 공개 JWKS의 kid/EC P-256 선택 → 실제 서명 → `iss=tc-ui-backend` / `aud=ai-gateway` → 정확 시각 → sub/scope/perm/pdg/경로별 dlg → 서비스 owner와 sys 바인딩 순으로 검증합니다. 원본 헤더에 `jku`·`jwk`·`x5u` 키가 있으면 null도 거부하며 네트워크 키 조회·헤더 키 사용은 없습니다. 혼합 JWKS의 정상 RSA 공개키가 있어도 RS256 요청을 허용하지 않습니다.

필수 클레임은 `iss`, `aud`, `sys`, `sub`, `scope`, `perm`, `pdg`, `iat`, `exp`입니다. sub는 `[A-Za-z0-9_-]{1,64}`, scope는 `READ`, perm은 `[A-Z0-9_]{1,64}` 문자열 배열이며 빈 배열만 유효한 빈 값입니다. pdg는 소문자 SHA-256 64자입니다. Gateway는 pdg를 재계산하지 않습니다.

서명 검증된 원본 UTF-8 payload를 중복 키 감지 streaming parser로 파싱합니다. iat/exp는 실제 JSON 숫자만 받으며 BigDecimal에서 손실 없이 Instant로 변환합니다. 공통 Clock을 한 번 읽은 시각으로 `iat <= now < exp` 및 `0 < exp - iat <= 60초`를 검사합니다. 60초+1ns, 미래 iat 1ns, exp 정각은 거부합니다. 문자열 coercion·Date/Double 변환·반올림·leeway는 없습니다.

POST `/v1/query`, `/v1/conversations/{id}/messages`, `/v1/analyses`는 dlg `[A-Za-z0-9_-]{22,128}`가 필수입니다. 나머지는 dlg 키가 있으면 null이라도 거부합니다. POST `/v1/conversations`는 비 Job입니다. Spring MVC와 같은 RequestPath/PathPattern으로 contextPath와 matrix parameter를 처리합니다.

모든 검증이 성공한 결과만 ScopeAssembler가 VerifiedSystemScope로 만들며 request attribute `ServiceTokenAuthFilter.class.getName() + ".scope"`에 전달합니다. Job 경로의 검증된 dlg만 `... + ".delegation"`으로 전달합니다. 구형 Body의 system/originSystem/targetSystem/authorizedSystems는 owner와 일치하는지만 검사합니다. 불일치·잘못된 시스템 값은 403이며 controller에는 재독 가능한 원래 Body를 넘깁니다.

## 자격 파일

`ai.gateway.security.service-tokens-file`과 `ai.gateway.security.actor-jwks-file`은 필수입니다. 전자는 `{"<소문자 SHA-256 64자>":"TC"}` 형식이고 owner는 TC/WM/HRM만 허용합니다. 후자는 공개 JWKS입니다. 누락·빈 집합·잘못된 JSON·중복 해시/kid·불법 공개키·알고리즘/curve 불일치·모든 깊이의 `d,p,q,dp,dq,qi,oth,k`는 기동 실패입니다. 파일은 한 번 읽어 불변 상태로 보유하며 교체에는 재기동이 필요합니다. 오류는 고정 한국어 문구로 바꾸고 파서/JCA 원인을 전달하지 않습니다.

## 거절 감사와 응답

기존 audit_event에 AUTH_REJECTED만 추가합니다. token missing/invalid는 owner/actor 모두 SQL NULL, 서비스 검증 뒤 단언 실패(sys 불일치 포함)는 owner만, 완전 검증 뒤 Body 위반은 owner+actor입니다. raw claim/토큰/JWS/dlg/Body/Throwable/JSON payload는 감사에 전달하지 않습니다. 서버 UUID, 공통 Clock의 마이크로초 내림 시각, 닫힌 사유와 401/403 status만 기록합니다. 일반 9개 저장소와 감사 조회는 기존 scope 격리를 유지하므로 신원 NULL 행을 노출하지 않습니다. 감사 자체 90일 보존과 NULL 포함 purge를 유지합니다.

감사 INSERT 및 실제 commit 성공 뒤에만 원래 401/403을 응답합니다. 저장·commit·연결·timeout 실패는 후속 처리 없이 HTTP 503, `Retry-After: 1`, `Content-Type: application/json`과 다음 두 필드만 반환합니다.

```json
{"message":"요청을 처리할 수 없습니다. 잠시 후 다시 시도해 주세요.","requestId":"<서버 UUID>"}
```

오류 원문·SQL·주소·자격·업무 error enum은 응답하지 않습니다. 재시도·로그 대체·실패 후 chain 진입은 없습니다. 인증 거절 시 대화·메시지·Job·Source·Tool·LLM·캐시 조회는 0이어야 합니다.

## 시험용 서명과 pdg

StubAssertionSigner는 tc-mock testFixtures에만 있습니다. `ephemeral(test-eph-...)`, `fromPrivateJwkFile(path)` 두 factory와 `sign(claims, providedInstant)`를 사용하며 iat는 제공 시각, exp는 정확히 +60초입니다. secret-file 오류는 ephemeral로 fallback하지 않습니다. 실제 배포 수용 suite는 승인된 secret-file만 사용해야 하며 P08은 배포 suite를 구현하지 않습니다. Gateway runtime에는 서명기·시험 인프라·개인키를 넣지 않습니다.

pdg 정본은 비어 있지 않고 CR/LF 없는 catalogRevision, 비어 있지 않고 공백 없는 permCode 목록을 중복 제거·Unicode 코드포인트 정렬한 뒤 `revision + "\n" + join(perms,"\n")`의 UTF-8 SHA-256입니다. 끝 개행을 추가하지 않습니다. 고정 벡터는 `gateway-tc-mock/src/test/resources/pdg-vectors-v1.json`의 V1~V4/N1~N3이며 Java와 기존 Python 도구가 기대값과 비교합니다.

V3.1을 먼저 migrator로 적용하고 최신 SchemaVersionGuard·자격·감사 실패 응답을 검증한 뒤 인입을 활성화합니다. rollback도 같은 schema와 승인 A 계약에 호환되는 앱만 허용합니다. 호환 앱이 없으면 인입 중단 후 forward-only 보정하며 기존 V 수정·미확인 감사 삭제·가짜 신원 채우기는 하지 않습니다.

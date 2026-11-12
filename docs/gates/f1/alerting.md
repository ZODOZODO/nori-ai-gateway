# G-ALERT · G-OBS-READBACK 상태 (P14)

## 현재 판정

| 게이트 | 상태 | 근거 |
|---|---|---|
| G-ALERT | NOT_RUN (미확인) | 운영 monitoring의 alert rule 형식·수신처 읽기 전용 확인 기록이 없다. exec `docs/observability/03-alert-policy.md`는 Prometheus rule·Alertmanager 1차안이며 실제 배포는 후속이라고 적는다. |
| G-OBS-READBACK | NOT_RUN (미확인) | Tempo·Loki·metric 읽기 경로와 Alloy `alloy-gateway`의 OTLP logs 수신 파이프라인 확인 기록이 없다. |

따라서 `ai.gateway.otlp.logs.enabled=false`(stdout JSON만)를 유지하고, AC-24-AUDIT-ALLOW 배포 판정과 경보 leaf는 NOT_RUN이다. 로컬 인프로세스 수신기 시험은 export 경로·허용 속성의 로컬 검증이며 운영 수신·경보 전달 PASS가 아니다.

## 수집 경로 (Plan §10.1)

- metrics: `micrometer-registry-otlp` 명시 registry → `http://alloy-gateway.monitoring.svc:4318/v1/metrics` (`ai.gateway.otlp.metrics.enabled=true`). Boot 4.0.7 OTLP metrics 자동구성은 `spring-boot-opentelemetry`의 `OpenTelemetryProperties` 클래스를 조건으로 요구하며 그 모듈이 runtime에 없어 활성화되지 않는다.
- traces: 명시 `SdkTracerProvider` + `OtlpHttpSpanExporter` → `/v1/traces`(`ai.gateway.otlp.traces.enabled=true`일 때만 exporter 연결, 꺼져도 SDK trace는 생성). 만드는 span은 두 가지다: 정상 인증 `/v1/*` 요청마다 `ServiceTokenAuthFilter`가 원격 부모 없이 만드는 `aigw.request`(SERVER, 속성 0)와 직접 Source 요청의 `aigw.source.request`(CLIENT, `source_id`·`method`·`status_class`). javaagent·tracing bridge·표준 HTTP handler 없음.
- logs: stdout `AllowlistJsonEncoder`. OTLP `/v1/logs`는 G-OBS-READBACK 확인 후 `ai.gateway.otlp.logs.enabled=true`로만 켠다.

## §11.6 경보와 신호 소유

| 경보 | 신호 | 소유 phase | 상태 |
|---|---|---|---|
| AIGW-RETENTION-PURGE-FAILED | `aigw_retention_purge_failures_total` + 고정 로그 코드 `RETENTION_PURGE_FAILED` (정리 Job 실패, 또는 성공한 주기 뒤 남은 가장 오래된 대화 만료가 그 주기 Gateway Clock보다 24h 초과 — 정각은 초과 아님, 주기당 1회) | P14 | 규칙 후보 `ops/monitoring/aigw-alerts.yaml`. 운영 rule 전달은 G-ALERT 미확인으로 NOT_RUN. |
| AIGW-INFERENCE-NOT-READY-PERSISTENT | `aigw_inference_alert{state}` | P19 | state 값 집합 미정 — 규칙 없음 |
| AIGW-INFERENCE-CONTEXT-MISMATCH | `aigw_inference_alert{state}` | P19 | 규칙 없음 |
| AIGW-BACKUP-FAILED / AIGW-BACKUP-STALE | — (2026-11-12 철회(seq17), SPEC §12.4 — 이력: `aigw_backup_last_run_failed`, `aigw_backup_last_success_timestamp_seconds`) | — (이력: P15) | 철회 — 지표·규칙 제거 |
| AIGW-DB-UNAVAILABLE | readiness DB DOWN 2분 | P15 | 지표 신호 없음 — 규칙 없음 |
| AIGW-INGEST-FAILING | `aigw_ingest_events_total{outcome="FAILED"}` | P24 | 임계값 F3 측정 후 |
| AIGW-RESTORE-SOURCE-UNVERIFIED | — (2026-11-12 철회(seq17), SPEC §12.4 — 이력: 복원 원천 대조) | — (이력: P27) | 철회 |

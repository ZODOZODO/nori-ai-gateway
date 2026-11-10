-- 감사에는 정본 허용 메타데이터만 별도 열로 저장합니다.
CREATE TABLE gw.audit_event (
    id bigserial PRIMARY KEY, recorded_at timestamptz NOT NULL, owner_system text NOT NULL CHECK (owner_system IN ('TC','WM','HRM')),
    actor_ref text NOT NULL, request_id text NOT NULL, conversation_id text, job_id text, trace_id text,
    event_type text NOT NULL, tool_id text, source_ref text, decision text, status text, error_code text,
    timings jsonb, token_usage jsonb, versions jsonb, reduction jsonb
);
CREATE TABLE gw.source_retention_probe (
    source_id text PRIMARY KEY, verified_oldest_at timestamptz, probe_limit_at timestamptz, probed_at timestamptz NOT NULL
);
CREATE TABLE gw.backup_run (
    id bigserial PRIMARY KEY, started_at timestamptz NOT NULL, finished_at timestamptz, outcome text NOT NULL,
    backup_point_at timestamptz, detail_code text
);
CREATE TABLE gw.instance_epoch (
    id integer PRIMARY KEY CHECK (id = 1), db_system_identifier text NOT NULL, recorded_at timestamptz NOT NULL,
    restore_completed_at timestamptz, restored_backup_point_at timestamptz
);

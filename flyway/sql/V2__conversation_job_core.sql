CREATE TABLE gw.conversation (
    id text PRIMARY KEY, owner_system text NOT NULL CHECK (owner_system IN ('TC','WM','HRM')),
    owner_actor_ref text NOT NULL, created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL CHECK (expires_at > created_at), status text NOT NULL,
    UNIQUE (id, owner_system)
);
CREATE INDEX conversation_expires_at_idx ON gw.conversation(expires_at);
CREATE TABLE gw.message (
    id text PRIMARY KEY, conversation_id text NOT NULL, owner_system text NOT NULL, role text NOT NULL,
    created_at timestamptz NOT NULL, content_masked text NOT NULL, resource_refs jsonb NOT NULL,
    tool_execution_refs jsonb NOT NULL, response_ref text, resolved_time_window jsonb,
    FOREIGN KEY (conversation_id, owner_system) REFERENCES gw.conversation(id, owner_system) ON DELETE CASCADE
);
CREATE TABLE gw.context (
    conversation_id text PRIMARY KEY, owner_system text NOT NULL, payload jsonb NOT NULL, updated_at timestamptz NOT NULL,
    FOREIGN KEY (conversation_id, owner_system) REFERENCES gw.conversation(id, owner_system) ON DELETE CASCADE
);
CREATE TABLE gw.conversation_summary (
    conversation_id text PRIMARY KEY, owner_system text NOT NULL, payload jsonb NOT NULL, updated_at timestamptz NOT NULL,
    FOREIGN KEY (conversation_id, owner_system) REFERENCES gw.conversation(id, owner_system) ON DELETE CASCADE
);
CREATE TABLE gw.job (
    id text PRIMARY KEY, seq bigserial UNIQUE NOT NULL, conversation_id text NOT NULL, owner_system text NOT NULL,
    actor_ref text NOT NULL, status text NOT NULL CHECK (status IN ('QUEUED','RUNNING','SUCCEEDED','PARTIAL','FAILED','CANCELLED','TIMED_OUT')),
    created_at timestamptz NOT NULL, started_at timestamptz, terminal_at timestamptz, error_code text,
    cancel_requested_at timestamptz, cancel_reason text, result_ref text, idempotency_key text,
    request_digest text NOT NULL, expected_path text NOT NULL, delegation_id text NULL,
    CONSTRAINT job_dlg_terminal_null CHECK (status IN ('QUEUED','RUNNING') OR delegation_id IS NULL),
    UNIQUE (id, owner_system),
    FOREIGN KEY (conversation_id, owner_system) REFERENCES gw.conversation(id, owner_system) ON DELETE CASCADE
);
CREATE INDEX job_status_seq_idx ON gw.job(status, seq);
CREATE INDEX job_conversation_idx ON gw.job(conversation_id);
CREATE UNIQUE INDEX job_idempotency_idx ON gw.job(owner_system, actor_ref, idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE TABLE gw.queue_control (id integer PRIMARY KEY CHECK (id = 1));
INSERT INTO gw.queue_control(id) VALUES (1);
CREATE TABLE gw.job_event (
    id bigserial PRIMARY KEY, job_id text NOT NULL, owner_system text NOT NULL, type text NOT NULL,
    payload jsonb NOT NULL, created_at timestamptz NOT NULL, published_at timestamptz,
    external_suppressed boolean NOT NULL DEFAULT false,
    FOREIGN KEY (job_id, owner_system) REFERENCES gw.job(id, owner_system) ON DELETE CASCADE
);
CREATE TABLE gw.result (
    job_id text PRIMARY KEY, owner_system text NOT NULL, status text NOT NULL, error_code text,
    payload jsonb NOT NULL, clarification jsonb, result_source_deps jsonb NOT NULL, created_at timestamptz NOT NULL,
    FOREIGN KEY (job_id, owner_system) REFERENCES gw.job(id, owner_system) ON DELETE CASCADE
);
CREATE TABLE gw.evidence (
    id text PRIMARY KEY, job_id text NOT NULL, owner_system text NOT NULL, source_ref text NOT NULL,
    chunk_id text, revision text, payload jsonb NOT NULL, created_at timestamptz NOT NULL,
    FOREIGN KEY (job_id, owner_system) REFERENCES gw.job(id, owner_system) ON DELETE CASCADE
);
CREATE TABLE gw.tool_execution (
    id text PRIMARY KEY, job_id text NOT NULL, owner_system text NOT NULL, capability_id text NOT NULL,
    status text NOT NULL, payload jsonb NOT NULL, created_at timestamptz NOT NULL,
    FOREIGN KEY (job_id, owner_system) REFERENCES gw.job(id, owner_system) ON DELETE CASCADE
);
CREATE TABLE gw.cache_entry (
    key_digest text NOT NULL, owner_system text NOT NULL, actor_ref text NOT NULL, pdg text NOT NULL,
    conversation_id text NOT NULL, capability_id text NOT NULL, value jsonb NOT NULL, source_deps jsonb NOT NULL,
    expires_at timestamptz NOT NULL, PRIMARY KEY (key_digest, owner_system, actor_ref, pdg),
    FOREIGN KEY (conversation_id, owner_system) REFERENCES gw.conversation(id, owner_system) ON DELETE CASCADE
);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM gw.job WHERE status IN ('QUEUED', 'RUNNING')) THEN
        RAISE EXCEPTION 'V7 requires no active gw.job; request audit origin cannot be inferred';
    END IF;
END $$;

ALTER TABLE gw.audit_event
    ADD COLUMN service_ref text,
    ADD COLUMN task_type text,
    ADD COLUMN result_status text;

ALTER TABLE gw.audit_event
    ADD CONSTRAINT audit_event_auth_rejected_new_fields_ck
        CHECK (event_type <> 'AUTH_REJECTED' OR (service_ref IS NULL AND task_type IS NULL AND result_status IS NULL)),
    ADD CONSTRAINT audit_event_request_origin_ck
        CHECK (event_type NOT IN ('API_REQUEST', 'JOB_ACCEPTED')
            OR (service_ref IS NOT NULL AND trace_id IS NOT NULL AND trace_id ~ '^[0-9a-f]{32}$' AND trace_id <> repeat('0', 32)));

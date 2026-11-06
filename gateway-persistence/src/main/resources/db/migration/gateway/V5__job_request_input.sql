DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM gw.job) THEN
        RAISE EXCEPTION 'V5 requires an empty gw.job; request input cannot be inferred';
    END IF;
END $$;

ALTER TABLE gw.message ADD CONSTRAINT message_request_identity UNIQUE (id, conversation_id, owner_system);
ALTER TABLE gw.job
    ADD COLUMN request_message_id text NOT NULL,
    ADD COLUMN request_input jsonb NOT NULL CHECK (jsonb_typeof(request_input) = 'object'),
    ADD CONSTRAINT job_request_message_fk FOREIGN KEY (request_message_id, conversation_id, owner_system)
        REFERENCES gw.message(id, conversation_id, owner_system) DEFERRABLE INITIALLY DEFERRED;

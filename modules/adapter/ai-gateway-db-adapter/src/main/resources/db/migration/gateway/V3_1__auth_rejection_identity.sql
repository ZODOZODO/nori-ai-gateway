ALTER TABLE gw.audit_event
    ALTER COLUMN owner_system DROP NOT NULL,
    ALTER COLUMN actor_ref DROP NOT NULL;

ALTER TABLE gw.audit_event
    ADD CONSTRAINT audit_event_verified_identity_ck
        CHECK (event_type = 'AUTH_REJECTED' OR (owner_system IS NOT NULL AND actor_ref IS NOT NULL)),
    ADD CONSTRAINT audit_event_actor_owner_ck
        CHECK (actor_ref IS NULL OR owner_system IS NOT NULL);

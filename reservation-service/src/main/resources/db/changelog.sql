--liquibase formatted sql
--changeset locker:reservations-1
CREATE TABLE booking (
    id uuid PRIMARY KEY, compartment text NOT NULL, owner_sub text NOT NULL, email text NOT NULL,
    state text NOT NULL CHECK (state IN ('RESERVED','DEPOSITED','COLLECTED','CANCELLED')),
    verifier text, expires_at timestamptz, attempts integer NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX one_active_booking ON booking(compartment) WHERE state IN ('RESERVED','DEPOSITED');
CREATE TABLE command_result (
    actor text NOT NULL, request_id uuid NOT NULL, fingerprint text NOT NULL, result bytea NOT NULL, error text,
    PRIMARY KEY(actor,request_id)
);
CREATE TABLE outbox (
    seq bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY, event_id uuid NOT NULL UNIQUE,
    reservation_id uuid NOT NULL, topic text NOT NULL, payload text NOT NULL
);

--changeset locker:outbox-tracing
ALTER TABLE outbox ADD COLUMN trace_parent text DEFAULT nullif(current_setting('locker.trace_parent', true), '');

--changeset locker:pickup-attempt-limit
ALTER TABLE booking ADD COLUMN pickup_max_attempts integer NOT NULL DEFAULT 5 CHECK (pickup_max_attempts > 0);

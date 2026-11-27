\getenv reader_password LAB_READER_PASSWORD
\getenv retention_hours LAB_RETENTION_HOURS
\if :{?retention_hours}
\else
\set retention_hours 72
\endif
BEGIN;
SELECT pg_advisory_xact_lock(817494);
DO $$ BEGIN
 IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='lab_reader') THEN
   CREATE ROLE lab_reader LOGIN;
 END IF;
END $$;
ALTER ROLE lab_reader PASSWORD :'reader_password';
ALTER ROLE lab_reader SET default_transaction_read_only = on;
CREATE SCHEMA IF NOT EXISTS lab_inspect;
CREATE TABLE IF NOT EXISTS lab_inspect.retention (
  singleton boolean PRIMARY KEY CHECK (singleton),
  hours integer NOT NULL CHECK (hours >= 24)
);
INSERT INTO lab_inspect.retention VALUES (true, :'retention_hours'::integer)
  ON CONFLICT (singleton) DO UPDATE SET hours=EXCLUDED.hours;
CREATE TABLE IF NOT EXISTS lab_inspect.row_change (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  at timestamptz NOT NULL DEFAULT clock_timestamp(), trace_id text,
  table_name text NOT NULL, action text NOT NULL, before_row jsonb, after_row jsonb
);
CREATE INDEX IF NOT EXISTS row_change_time ON lab_inspect.row_change(at);
CREATE INDEX IF NOT EXISTS row_change_trace ON lab_inspect.row_change(trace_id, at);

CREATE OR REPLACE VIEW lab_inspect.compartment AS SELECT id,site,size FROM directory.compartment;
CREATE OR REPLACE VIEW lab_inspect.booking AS SELECT id,compartment,state,expires_at,attempts,pickup_max_attempts FROM reservations.booking;
CREATE OR REPLACE VIEW lab_inspect.command_result AS
 SELECT request_id, CASE WHEN error IS NULL THEN 'accepted' ELSE 'rejected' END AS outcome FROM reservations.command_result;
CREATE OR REPLACE VIEW lab_inspect.outbox AS SELECT seq,event_id,reservation_id,topic FROM reservations.outbox;
CREATE OR REPLACE VIEW lab_inspect.changes AS SELECT * FROM lab_inspect.row_change WHERE at >= now() - (SELECT make_interval(hours => hours) FROM lab_inspect.retention);

-- Use the inspector's view columns as the allowlist for both sides of each change.
CREATE OR REPLACE FUNCTION lab_inspect.project(row_data jsonb, table_name text, columns text[])
RETURNS jsonb LANGUAGE sql IMMUTABLE STRICT AS $$
  SELECT jsonb_object_agg(col, CASE
    WHEN table_name='command_result' AND col='outcome' THEN
      to_jsonb(CASE WHEN row_data->>'error' IS NULL THEN 'accepted' ELSE 'rejected' END)
    ELSE row_data->col END)
  FROM unnest(columns) col
$$;
CREATE OR REPLACE FUNCTION lab_inspect.capture() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog AS $$
DECLARE
  prev jsonb;
  next jsonb;
  parent text := current_setting('locker.trace_parent', true);
BEGIN
  IF TG_OP <> 'INSERT' THEN
    prev := lab_inspect.project(to_jsonb(OLD),TG_TABLE_NAME,TG_ARGV);
    IF TG_TABLE_NAME='outbox' THEN parent := coalesce(OLD.trace_parent,parent); END IF;
  END IF;
  IF TG_OP <> 'DELETE' THEN
    next := lab_inspect.project(to_jsonb(NEW),TG_TABLE_NAME,TG_ARGV);
  END IF;
  INSERT INTO lab_inspect.row_change(trace_id,table_name,action,before_row,after_row)
      VALUES(nullif(split_part(parent,'-',2),''),TG_TABLE_NAME,TG_OP,prev,next);
  DELETE FROM lab_inspect.row_change WHERE at < now() - (SELECT make_interval(hours => hours) FROM lab_inspect.retention);
  RETURN NULL;
END $$;
REVOKE ALL ON FUNCTION lab_inspect.capture() FROM PUBLIC;
DO $$ DECLARE
  view_name text;
  owner_schema text;
  columns text;
BEGIN
  FOREACH view_name IN ARRAY ARRAY['compartment','booking','command_result','outbox'] LOOP
    owner_schema := CASE WHEN view_name='compartment' THEN 'directory' ELSE 'reservations' END;
    SELECT string_agg(quote_literal(column_name),',' ORDER BY ordinal_position) INTO STRICT columns
      FROM information_schema.columns WHERE table_schema='lab_inspect' AND table_name=view_name;
    EXECUTE format('CREATE OR REPLACE TRIGGER lab_row_change AFTER INSERT OR UPDATE OR DELETE ON %I.%I FOR EACH ROW EXECUTE FUNCTION lab_inspect.capture(%s)',owner_schema,view_name,columns);
  END LOOP;
END $$;
GRANT USAGE ON SCHEMA lab_inspect TO lab_reader;
GRANT SELECT ON lab_inspect.compartment, lab_inspect.booking, lab_inspect.command_result, lab_inspect.outbox, lab_inspect.changes TO lab_reader;
COMMIT;

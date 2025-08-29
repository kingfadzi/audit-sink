-- V1__init.sql
DROP TABLE IF EXISTS audit_event CASCADE;
CREATE TABLE IF NOT EXISTS audit_event (
  id UUID PRIMARY KEY,
  occurred_at_utc TIMESTAMP NOT NULL,
  action VARCHAR(128) NOT NULL,
  outcome VARCHAR(32) NOT NULL,
  subject_type VARCHAR(64) NOT NULL,
  subject_id VARCHAR(256) NOT NULL,
  actor_id VARCHAR(256) NOT NULL,
  actor_type VARCHAR(32) NOT NULL,
  roles VARCHAR(1024),
  tenant_id VARCHAR(128),
  channel VARCHAR(32),
  ip VARCHAR(128),
  user_agent VARCHAR(1024),
  correlation_id VARCHAR(256),
  trace_id VARCHAR(256),
  app_id VARCHAR(128),
  track_id VARCHAR(128),
  release_id VARCHAR(128),
  jira_key VARCHAR(128),
  snow_sys_id VARCHAR(128),
  policy_decision_id VARCHAR(256),
  rule_path VARCHAR(512),
  payload_hash VARCHAR(128),
  args_redacted TEXT,
  result_redacted TEXT,
  error_type VARCHAR(256),
  error_message_hash VARCHAR(128),
  schema_version INT NOT NULL DEFAULT 1,
  idempotency_key VARCHAR(256) UNIQUE
);

CREATE INDEX IF NOT EXISTS idx_audit_event_time ON audit_event(occurred_at_utc);
CREATE INDEX IF NOT EXISTS idx_audit_event_app_time ON audit_event(app_id, occurred_at_utc);
CREATE INDEX IF NOT EXISTS idx_audit_event_track_time ON audit_event(track_id, occurred_at_utc);
CREATE INDEX IF NOT EXISTS idx_audit_event_action_time ON audit_event(action, occurred_at_utc);
CREATE INDEX IF NOT EXISTS idx_audit_event_subject_time ON audit_event(subject_type, subject_id, occurred_at_utc);
CREATE INDEX IF NOT EXISTS idx_audit_event_corr ON audit_event(correlation_id);

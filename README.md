# Audit Sink Service (MVP)

A minimal Spring Boot service that ingests audit events via HTTP and stores them in the **audit** schema (same Postgres cluster or in-memory H2 for quick start).

## Features
- `POST /audit/events` to ingest a single event
- Validation, redaction (key-based), size caps
- Idempotency using provided `idempotencyKey` or computed from producerId+correlationId+action+subject
- Unique constraint on `idempotency_key` to dedupe
- Actuator: `/actuator/health`, `/actuator/metrics`, `/actuator/prometheus`
- Optional API key auth via `X-Api-Key`

## Quick start (H2 in-memory)
```bash
# requires Java 17+ and Maven
cd audit-sink
mvn spring-boot:run
```

Send a test event:
```bash
curl -i -X POST http://localhost:8080/audit/events   -H 'Content-Type: application/json'   -H 'X-Api-Key: dev-key'   -d '{
    "schemaVersion": 1,
    "producerId": "po-service",
    "occurredAtUtc": "2025-08-29T14:55:21Z",
    "action": "EVIDENCE_APPROVED",
    "outcome": "SUCCESS",
    "subject": {"type":"evidence","id":"ev_123"},
    "actor": {"id":"u_1","type":"USER","roles":["PO","APP_OWNER"],"tenantId":"bank-na"},
    "context": {"appId":"APP-42","trackId":"TR-9","releaseId":"R-1","jiraKey":"GOV-101","snowSysId":"SYS-1"},
    "channel": "UI",
    "correlationId": "corr-abc",
    "traceId": "trace-xyz",
    "policy": {"decisionId":"pol-1","rulePath":"controls.evidence.approval"},
    "payload": {"argsRedacted":{"token":"abc","note":"hello"},"resultRedacted":{"status":"approved"},"payloadHash":"sha256:..."},
    "error": null
  }'
```

Check tables (H2 console is not enabled by default; for Postgres, connect to your DB).

## Run with Postgres
Create DB & user, then set profile:
```bash
export SPRING_PROFILES_ACTIVE=postgres
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/auditdb
export SPRING_DATASOURCE_USERNAME=audit_user
export SPRING_DATASOURCE_PASSWORD=change-me
mvn spring-boot:run
```

## Configuration
- `audit.auth.apiKey`: if set, requests must include `X-Api-Key` with the same value.
- `audit.redaction.redactKeys`: keys to mask in payloads.
- `audit.payload.maxJsonBytes`: maximum serialized size (bytes) of the redacted `argsRedacted`/`resultRedacted` stored per event.

## Notes
- Schema & indices are created by Flyway under schema `audit`.
- For production, configure Postgres and set a strong `audit.auth.apiKey` or replace with JWT/mTLS.
- All JSON payloads are stored as text in MVP for cross-DB compatibility. You can migrate to JSONB in Postgres later.

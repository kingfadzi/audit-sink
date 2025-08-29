package dev.controlplane.auditsink.store;

import java.time.OffsetDateTime;
import java.util.UUID;

public class AuditEventEntity {
    public UUID id;
    public OffsetDateTime occurredAtUtc;
    public String action;
    public String outcome;
    public String subjectType;
    public String subjectId;
    public String actorId;
    public String actorType;
    public String roles; // comma-separated
    public String tenantId;
    public String channel;
    public String ip;
    public String userAgent;
    public String correlationId;
    public String traceId;
    public String appId;
    public String trackId;
    public String releaseId;
    public String jiraKey;
    public String snowSysId;
    public String policyDecisionId;
    public String rulePath;
    public String payloadHash;
    public String argsRedacted;   // JSON string (redacted, size-capped)
    public String resultRedacted; // JSON string (redacted, size-capped)
    public String errorType;
    public String errorMessageHash;
    public Integer schemaVersion;
    public String idempotencyKey;
}

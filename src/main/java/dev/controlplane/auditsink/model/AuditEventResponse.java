package dev.controlplane.auditsink.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AuditEventResponse(
    UUID id,
    OffsetDateTime occurredAtUtc,
    String action,
    String outcome,
    String subjectType,
    String subjectId,
    String actorId,
    String actorType,
    String roles,
    String tenantId,
    String channel,
    String ip,
    String userAgent,
    String correlationId,
    String traceId,
    String appId,
    String trackId,
    String releaseId,
    String jiraKey,
    String snowSysId,
    String policyDecisionId,
    String rulePath,
    String payloadHash,
    String argsRedacted,
    String resultRedacted,
    String errorType,
    String errorMessageHash,
    Integer schemaVersion,
    String idempotencyKey
) {}
package dev.controlplane.auditsink.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record AuditEventRequest(
        Integer schemaVersion,
        @NotBlank String producerId,
        @NotNull OffsetDateTime occurredAtUtc,
        @NotBlank String action,
        @NotBlank String outcome,
        @NotNull Subject subject,
        @NotNull Actor actor,
        Context context,
        String channel,
        String correlationId,
        String traceId,
        Policy policy,
        Payload payload,
        ErrorInfo error,
        String idempotencyKey
) {
    public record Subject(@NotBlank String type, @NotBlank String id) {}
    public record Actor(@NotBlank String id, @NotBlank String type, List<String> roles, String tenantId) {}
    public record Context(String appId, String trackId, String releaseId, String jiraKey, String snowSysId) {}
    public record Policy(String decisionId, String rulePath) {}
    public record Payload(Map<String,Object> argsRedacted, Map<String,Object> resultRedacted, String payloadHash) {}
    public record ErrorInfo(String errorType, String errorMessageHash) {}
}

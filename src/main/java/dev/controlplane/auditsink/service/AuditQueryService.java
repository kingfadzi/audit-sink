package dev.controlplane.auditsink.service;

import dev.controlplane.auditsink.model.AuditEventResponse;
import dev.controlplane.auditsink.model.PagedResponse;
import dev.controlplane.auditsink.store.AuditEventEntity;
import dev.controlplane.auditsink.store.AuditEventRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuditQueryService {
    
    private final AuditEventRepository repository;
    
    public AuditQueryService(AuditEventRepository repository) {
        this.repository = repository;
    }
    
    public PagedResponse<AuditEventResponse> getEvents(int page, int size, String sortBy, String sortOrder) {
        String validSortBy = validateSortField(sortBy);
        List<AuditEventEntity> entities = repository.findAll(page, size, validSortBy, sortOrder);
        long totalElements = repository.count();
        
        List<AuditEventResponse> responses = entities.stream()
            .map(this::mapToResponse)
            .toList();
            
        return PagedResponse.of(responses, page, size, totalElements);
    }
    
    public Optional<AuditEventResponse> getEventById(UUID id) {
        return repository.findById(id)
            .map(this::mapToResponse);
    }
    
    public PagedResponse<AuditEventResponse> searchEvents(
            String tenantId,
            String actorId,
            String subjectId,
            String action,
            String outcome,
            String correlationId,
            String traceId,
            String appId,
            OffsetDateTime fromDate,
            OffsetDateTime toDate,
            int page,
            int size,
            String sortBy,
            String sortOrder) {
        
        Map<String, Object> filters = new HashMap<>();
        if (tenantId != null) filters.put("tenantId", tenantId);
        if (actorId != null) filters.put("actorId", actorId);
        if (subjectId != null) filters.put("subjectId", subjectId);
        if (action != null) filters.put("action", action);
        if (outcome != null) filters.put("outcome", outcome);
        if (correlationId != null) filters.put("correlationId", correlationId);
        if (traceId != null) filters.put("traceId", traceId);
        if (appId != null) filters.put("appId", appId);
        if (fromDate != null) filters.put("fromDate", fromDate);
        if (toDate != null) filters.put("toDate", toDate);
        
        String validSortBy = validateSortField(sortBy);
        List<AuditEventEntity> entities = repository.search(filters, page, size, validSortBy, sortOrder);
        long totalElements = repository.countWithFilters(filters);
        
        List<AuditEventResponse> responses = entities.stream()
            .map(this::mapToResponse)
            .toList();
            
        return PagedResponse.of(responses, page, size, totalElements);
    }
    
    private String validateSortField(String sortBy) {
        return switch (sortBy == null ? "occurred_at_utc" : sortBy) {
            case "id", "occurred_at_utc", "action", "outcome", "actor_id", "subject_id", "tenant_id" -> sortBy;
            default -> "occurred_at_utc";
        };
    }
    
    private AuditEventResponse mapToResponse(AuditEventEntity entity) {
        return new AuditEventResponse(
            entity.id,
            entity.occurredAtUtc,
            entity.action,
            entity.outcome,
            entity.subjectType,
            entity.subjectId,
            entity.actorId,
            entity.actorType,
            entity.roles,
            entity.tenantId,
            entity.channel,
            entity.ip,
            entity.userAgent,
            entity.correlationId,
            entity.traceId,
            entity.appId,
            entity.trackId,
            entity.releaseId,
            entity.jiraKey,
            entity.snowSysId,
            entity.policyDecisionId,
            entity.rulePath,
            entity.payloadHash,
            entity.argsRedacted,
            entity.resultRedacted,
            entity.errorType,
            entity.errorMessageHash,
            entity.schemaVersion,
            entity.idempotencyKey
        );
    }
}
package dev.controlplane.auditsink.web;

import dev.controlplane.auditsink.model.AuditEventRequest;
import dev.controlplane.auditsink.model.AuditEventResponse;
import dev.controlplane.auditsink.model.IngestResponse;
import dev.controlplane.auditsink.model.PagedResponse;
import dev.controlplane.auditsink.service.AuditIngestService;
import dev.controlplane.auditsink.service.AuditQueryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/audit")
public class AuditController {

    private final AuditIngestService ingestService;
    private final AuditQueryService queryService;

    public AuditController(AuditIngestService ingestService, AuditQueryService queryService) {
        this.ingestService = ingestService;
        this.queryService = queryService;
    }

    @PostMapping("/events")
    public ResponseEntity<IngestResponse> ingest(@Valid @RequestBody AuditEventRequest req, HttpServletRequest http) {
        IngestResponse resp = ingestService.ingest(req, http);
        if (resp.deduped()) {
            return ResponseEntity.ok(resp);
        } else {
            return ResponseEntity.accepted().body(resp);
        }
    }

    @GetMapping("/health")
    public String health() {
        return "ok";
    }

    @GetMapping("/events")
    public ResponseEntity<PagedResponse<AuditEventResponse>> getEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "occurred_at_utc") String sortBy,
            @RequestParam(defaultValue = "desc") String sortOrder) {
        
        PagedResponse<AuditEventResponse> response = queryService.getEvents(page, size, sortBy, sortOrder);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/events/{id}")
    public ResponseEntity<AuditEventResponse> getEventById(@PathVariable UUID id) {
        return queryService.getEventById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/events/search")
    public ResponseEntity<PagedResponse<AuditEventResponse>> searchEvents(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String subjectId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) String correlationId,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) String appId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "occurred_at_utc") String sortBy,
            @RequestParam(defaultValue = "desc") String sortOrder) {

        PagedResponse<AuditEventResponse> response = queryService.searchEvents(
            tenantId, actorId, subjectId, action, outcome, 
            correlationId, traceId, appId, fromDate, toDate,
            page, size, sortBy, sortOrder
        );
        return ResponseEntity.ok(response);
    }
}

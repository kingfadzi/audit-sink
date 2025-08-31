package dev.controlplane.auditsink.web;

import dev.controlplane.auditsink.model.AuditEventRequest;
import dev.controlplane.auditsink.model.AuditEventResponse;
import dev.controlplane.auditsink.model.IngestResponse;
import dev.controlplane.auditsink.model.PagedResponse;
import dev.controlplane.auditsink.service.AuditIngestService;
import dev.controlplane.auditsink.service.AuditQueryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/audit")
public class AuditController {
    
    private static final Logger log = LoggerFactory.getLogger(AuditController.class);

    private final AuditIngestService ingestService;
    private final AuditQueryService queryService;

    public AuditController(AuditIngestService ingestService, AuditQueryService queryService) {
        this.ingestService = ingestService;
        this.queryService = queryService;
    }

    @PostMapping("/events")
    public ResponseEntity<IngestResponse> ingest(@Valid @RequestBody AuditEventRequest req, HttpServletRequest http) {
        try {
            log.info("Received audit event request: action={}, producerId={}, clientIP={}", 
                    req.action(), req.producerId(), clientIp(http));
            
            IngestResponse resp = ingestService.ingest(req, http);
            
            HttpStatus status = resp.deduped() ? HttpStatus.OK : HttpStatus.ACCEPTED;
            log.info("Audit event processed: eventId={}, deduped={}, status={}", 
                    resp.eventId(), resp.deduped(), status.value());
            
            return ResponseEntity.status(status).body(resp);
        } catch (Exception ex) {
            log.error("Error processing audit event: action={}, producerId={}, error={}", 
                    req.action(), req.producerId(), ex.getMessage(), ex);
            throw ex;
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

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidationExceptions(MethodArgumentNotValidException ex) {
        StringBuilder errors = new StringBuilder("Validation failed: ");
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.append(error.getField()).append(" ").append(error.getDefaultMessage()).append("; ");
        }
        
        log.warn("Validation error in audit event request: {}", errors.toString());
        return ResponseEntity.badRequest()
                .body(Map.of("error", "validation_failed", "message", errors.toString()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGenericException(Exception ex) {
        log.error("Unexpected error in audit controller: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "internal_error", "message", "An internal error occurred"));
    }

    private String clientIp(HttpServletRequest http) {
        String xff = http.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return http.getRemoteAddr();
    }
}

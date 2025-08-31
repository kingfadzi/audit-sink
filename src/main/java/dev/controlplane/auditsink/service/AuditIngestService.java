package dev.controlplane.auditsink.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.controlplane.auditsink.model.AuditEventRequest;
import dev.controlplane.auditsink.model.IngestResponse;
import dev.controlplane.auditsink.store.AuditEventEntity;
import dev.controlplane.auditsink.store.AuditEventRepository;
import dev.controlplane.auditsink.util.HashingUtil;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AuditIngestService {
    
    private static final Logger log = LoggerFactory.getLogger(AuditIngestService.class);

    private final AuditEventRepository repo;
    private final RedactionService redactionService;
    private final Counter receivedCounter;
    private final Counter ingestedCounter;
    private final Counter dedupCounter;
    private final Counter rejectedCounter;
    private final ObjectMapper mapper = new ObjectMapper();

    public AuditIngestService(AuditEventRepository repo, RedactionService redactionService, MeterRegistry registry) {
        this.repo = repo;
        this.redactionService = redactionService;
        this.receivedCounter = registry.counter("audit.events.received");
        this.ingestedCounter = registry.counter("audit.events.ingested");
        this.dedupCounter = registry.counter("audit.events.deduped");
        this.rejectedCounter = registry.counter("audit.events.rejected");
    }

    public IngestResponse ingest(AuditEventRequest req, HttpServletRequest http) {
        receivedCounter.increment();
        
        log.info("Processing audit event: action={}, outcome={}, subject={}:{}, actor={}:{}, producerId={}", 
                req.action(), req.outcome(), req.subject().type(), req.subject().id(), 
                req.actor().type(), req.actor().id(), req.producerId());

        AuditEventEntity e = new AuditEventEntity();
        e.id = UUID.randomUUID();
        e.schemaVersion = Optional.ofNullable(req.schemaVersion()).orElse(1);
        e.occurredAtUtc = Optional.ofNullable(req.occurredAtUtc()).orElse(OffsetDateTime.now());
        e.action = req.action();
        e.outcome = req.outcome();
        e.subjectType = req.subject().type();
        e.subjectId = req.subject().id();
        e.actorId = req.actor().id();
        e.actorType = req.actor().type();
        e.roles = req.actor().roles() != null ? req.actor().roles().stream().collect(Collectors.joining(",")) : null;
        e.tenantId = req.actor().tenantId();
        e.channel = req.channel();
        e.ip = clientIp(http);
        e.userAgent = http.getHeader("User-Agent");
        e.correlationId = req.correlationId();
        e.traceId = req.traceId();
        if (req.context() != null) {
            e.appId = req.context().appId();
            e.trackId = req.context().trackId();
            e.releaseId = req.context().releaseId();
            e.jiraKey = req.context().jiraKey();
            e.snowSysId = req.context().snowSysId();
        }
        if (req.policy() != null) {
            e.policyDecisionId = req.policy().decisionId();
            e.rulePath = req.policy().rulePath();
        }
        if (req.payload() != null) {
            e.argsRedacted = redactionService.redactAndCap(req.payload().argsRedacted());
            e.resultRedacted = redactionService.redactAndCap(req.payload().resultRedacted());
            e.payloadHash = req.payload().payloadHash();
        }
        if (req.error() != null) {
            e.errorType = req.error().errorType();
            e.errorMessageHash = req.error().errorMessageHash();
        }
        e.idempotencyKey = computeIdempotencyKey(req, http);
        
        if (req.idempotencyKey() != null) {
            log.warn("Client provided idempotencyKey '{}' ignored - using server-generated: '{}'", 
                    req.idempotencyKey(), e.idempotencyKey);
        }
        log.debug("Using server-generated idempotency key: {}", e.idempotencyKey);
        log.debug("Entity ready for insert: id={}, occurredAtUtc={}, action={}", e.id, e.occurredAtUtc, e.action);

        try {
            UUID id = repo.insert(e);
            ingestedCounter.increment();
            log.info("Successfully ingested audit event: eventId={}, action={}, deduped=false", id, req.action());
            return new IngestResponse(id.toString(), false);
        } catch (DataIntegrityViolationException dup) {
            dedupCounter.increment();
            UUID existing = repo.findByIdempotencyKey(e.idempotencyKey).orElse(UUID.randomUUID());
            log.info("Duplicate audit event detected: existingId={}, action={}, deduped=true, idempotencyKey={}", 
                    existing, req.action(), e.idempotencyKey);
            return new IngestResponse(existing.toString(), true);
        } catch (Exception ex) {
            rejectedCounter.increment();
            log.error("Failed to ingest audit event: action={}, subject={}:{}, error={}", 
                    req.action(), req.subject().type(), req.subject().id(), ex.getMessage(), ex);
            throw ex;
        }
    }

    private String computeIdempotencyKey(AuditEventRequest req, HttpServletRequest http) {
        try {
            String payloadJson = mapper.writeValueAsString(req);
            String payloadHash = HashingUtil.sha256Base64(payloadJson);
            
            String s = String.join("|",
                    "p:" + req.producerId(),
                    "ip:" + clientIp(http),
                    "ua:" + nullSafe(http.getHeader("User-Agent")),
                    "ts:" + req.occurredAtUtc().toString(),
                    "payload:" + payloadHash);
            return HashingUtil.sha256Base64(s);
        } catch (JsonProcessingException e) {
            String fallback = String.join("|",
                    "p:" + req.producerId(),
                    "ip:" + clientIp(http),
                    "ts:" + req.occurredAtUtc().toString(),
                    "a:" + req.action(),
                    "st:" + req.subject().type(),
                    "si:" + req.subject().id());
            return HashingUtil.sha256Base64(fallback);
        }
    }

    private String nullSafe(String s) { return s == null ? "" : s; }

    private String clientIp(HttpServletRequest http) {
        String xff = http.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return http.getRemoteAddr();
    }
}

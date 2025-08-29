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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AuditIngestService {

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
        e.idempotencyKey = req.idempotencyKey() != null ? req.idempotencyKey() : computeIdempotencyKey(req);

        try {
            UUID id = repo.insert(e);
            ingestedCounter.increment();
            return new IngestResponse(id.toString(), false);
        } catch (DataIntegrityViolationException dup) {
            dedupCounter.increment();
            UUID existing = repo.findByIdempotencyKey(e.idempotencyKey).orElse(UUID.randomUUID());
            return new IngestResponse(existing.toString(), true);
        } catch (Exception ex) {
            rejectedCounter.increment();
            throw ex;
        }
    }

    private String computeIdempotencyKey(AuditEventRequest req) {
        String s = String.join("|",
                "p:"+req.producerId(),
                "c:"+nullSafe(req.correlationId()),
                "a:"+req.action(),
                "st:"+req.subject().type(),
                "si:"+req.subject().id());
        return HashingUtil.sha256Base64(s);
    }

    private String nullSafe(String s) { return s == null ? "" : s; }

    private String clientIp(HttpServletRequest http) {
        String xff = http.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return http.getRemoteAddr();
    }
}

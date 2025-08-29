package dev.controlplane.auditsink.store;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AuditEventRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final RowMapper<AuditEventEntity> auditEventRowMapper;

    public AuditEventRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.auditEventRowMapper = (rs, rowNum) -> {
            AuditEventEntity entity = new AuditEventEntity();
            entity.id = UUID.fromString(rs.getString("id"));
            entity.occurredAtUtc = rs.getObject("occurred_at_utc", OffsetDateTime.class);
            entity.action = rs.getString("action");
            entity.outcome = rs.getString("outcome");
            entity.subjectType = rs.getString("subject_type");
            entity.subjectId = rs.getString("subject_id");
            entity.actorId = rs.getString("actor_id");
            entity.actorType = rs.getString("actor_type");
            entity.roles = rs.getString("roles");
            entity.tenantId = rs.getString("tenant_id");
            entity.channel = rs.getString("channel");
            entity.ip = rs.getString("ip");
            entity.userAgent = rs.getString("user_agent");
            entity.correlationId = rs.getString("correlation_id");
            entity.traceId = rs.getString("trace_id");
            entity.appId = rs.getString("app_id");
            entity.trackId = rs.getString("track_id");
            entity.releaseId = rs.getString("release_id");
            entity.jiraKey = rs.getString("jira_key");
            entity.snowSysId = rs.getString("snow_sys_id");
            entity.policyDecisionId = rs.getString("policy_decision_id");
            entity.rulePath = rs.getString("rule_path");
            entity.payloadHash = rs.getString("payload_hash");
            entity.argsRedacted = rs.getString("args_redacted");
            entity.resultRedacted = rs.getString("result_redacted");
            entity.errorType = rs.getString("error_type");
            entity.errorMessageHash = rs.getString("error_message_hash");
            entity.schemaVersion = rs.getObject("schema_version", Integer.class);
            entity.idempotencyKey = rs.getString("idempotency_key");
            return entity;
        };
    }

    public UUID insert(AuditEventEntity e) {
        String sql = """
            INSERT INTO audit_event(
              id, occurred_at_utc, action, outcome, subject_type, subject_id,
              actor_id, actor_type, roles, tenant_id, channel, ip, user_agent,
              correlation_id, trace_id, app_id, track_id, release_id, jira_key, snow_sys_id,
              policy_decision_id, rule_path, payload_hash, args_redacted, result_redacted,
              error_type, error_message_hash, schema_version, idempotency_key
            ) VALUES (
              :id, :occurred_at_utc, :action, :outcome, :subject_type, :subject_id,
              :actor_id, :actor_type, :roles, :tenant_id, :channel, :ip, :user_agent,
              :correlation_id, :trace_id, :app_id, :track_id, :release_id, :jira_key, :snow_sys_id,
              :policy_decision_id, :rule_path, :payload_hash, :args_redacted, :result_redacted,
              :error_type, :error_message_hash, :schema_version, :idempotency_key
            )
        """;

        MapSqlParameterSource ps = new MapSqlParameterSource();
        ps.addValue("id", e.id);
        ps.addValue("occurred_at_utc", e.occurredAtUtc);
        ps.addValue("action", e.action);
        ps.addValue("outcome", e.outcome);
        ps.addValue("subject_type", e.subjectType);
        ps.addValue("subject_id", e.subjectId);
        ps.addValue("actor_id", e.actorId);
        ps.addValue("actor_type", e.actorType);
        ps.addValue("roles", e.roles);
        ps.addValue("tenant_id", e.tenantId);
        ps.addValue("channel", e.channel);
        ps.addValue("ip", e.ip);
        ps.addValue("user_agent", e.userAgent);
        ps.addValue("correlation_id", e.correlationId);
        ps.addValue("trace_id", e.traceId);
        ps.addValue("app_id", e.appId);
        ps.addValue("track_id", e.trackId);
        ps.addValue("release_id", e.releaseId);
        ps.addValue("jira_key", e.jiraKey);
        ps.addValue("snow_sys_id", e.snowSysId);
        ps.addValue("policy_decision_id", e.policyDecisionId);
        ps.addValue("rule_path", e.rulePath);
        ps.addValue("payload_hash", e.payloadHash);
        ps.addValue("args_redacted", e.argsRedacted);
        ps.addValue("result_redacted", e.resultRedacted);
        ps.addValue("error_type", e.errorType);
        ps.addValue("error_message_hash", e.errorMessageHash);
        ps.addValue("schema_version", e.schemaVersion);
        ps.addValue("idempotency_key", e.idempotencyKey);
        try {
            jdbc.update(sql, ps);
            return e.id;
        } catch (DataIntegrityViolationException dup) {
            throw dup;
        }
    }

    public Optional<UUID> findByIdempotencyKey(String key) {
        String q = "SELECT id FROM audit_event WHERE idempotency_key = :k";
        return jdbc.query(q, new MapSqlParameterSource("k", key),
                rs -> rs.next() ? Optional.of(UUID.fromString(rs.getString("id"))) : Optional.empty());
    }

    public List<AuditEventEntity> findAll(int page, int size, String sortBy, String sortOrder) {
        String order = "DESC".equalsIgnoreCase(sortOrder) ? "DESC" : "ASC";
        String sql = String.format("""
            SELECT * FROM audit_event 
            ORDER BY %s %s 
            LIMIT :limit OFFSET :offset
            """, sortBy, order);
        
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("limit", size);
        params.addValue("offset", page * size);
        
        return jdbc.query(sql, params, auditEventRowMapper);
    }

    public Optional<AuditEventEntity> findById(UUID id) {
        String sql = "SELECT * FROM audit_event WHERE id = :id";
        MapSqlParameterSource params = new MapSqlParameterSource("id", id);
        
        List<AuditEventEntity> results = jdbc.query(sql, params, auditEventRowMapper);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public List<AuditEventEntity> search(Map<String, Object> filters, int page, int size, String sortBy, String sortOrder) {
        StringBuilder sql = new StringBuilder("SELECT * FROM audit_event WHERE 1=1");
        MapSqlParameterSource params = new MapSqlParameterSource();
        
        if (filters.containsKey("tenantId")) {
            sql.append(" AND tenant_id = :tenantId");
            params.addValue("tenantId", filters.get("tenantId"));
        }
        if (filters.containsKey("actorId")) {
            sql.append(" AND actor_id = :actorId");
            params.addValue("actorId", filters.get("actorId"));
        }
        if (filters.containsKey("subjectId")) {
            sql.append(" AND subject_id = :subjectId");
            params.addValue("subjectId", filters.get("subjectId"));
        }
        if (filters.containsKey("action")) {
            sql.append(" AND action = :action");
            params.addValue("action", filters.get("action"));
        }
        if (filters.containsKey("outcome")) {
            sql.append(" AND outcome = :outcome");
            params.addValue("outcome", filters.get("outcome"));
        }
        if (filters.containsKey("correlationId")) {
            sql.append(" AND correlation_id = :correlationId");
            params.addValue("correlationId", filters.get("correlationId"));
        }
        if (filters.containsKey("traceId")) {
            sql.append(" AND trace_id = :traceId");
            params.addValue("traceId", filters.get("traceId"));
        }
        if (filters.containsKey("appId")) {
            sql.append(" AND app_id = :appId");
            params.addValue("appId", filters.get("appId"));
        }
        if (filters.containsKey("fromDate")) {
            sql.append(" AND occurred_at_utc >= :fromDate");
            params.addValue("fromDate", filters.get("fromDate"));
        }
        if (filters.containsKey("toDate")) {
            sql.append(" AND occurred_at_utc <= :toDate");
            params.addValue("toDate", filters.get("toDate"));
        }
        
        String order = "DESC".equalsIgnoreCase(sortOrder) ? "DESC" : "ASC";
        sql.append(String.format(" ORDER BY %s %s LIMIT :limit OFFSET :offset", sortBy, order));
        
        params.addValue("limit", size);
        params.addValue("offset", page * size);
        
        return jdbc.query(sql.toString(), params, auditEventRowMapper);
    }

    public long count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM audit_event", new MapSqlParameterSource(), Long.class);
    }

    public long countWithFilters(Map<String, Object> filters) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM audit_event WHERE 1=1");
        MapSqlParameterSource params = new MapSqlParameterSource();
        
        if (filters.containsKey("tenantId")) {
            sql.append(" AND tenant_id = :tenantId");
            params.addValue("tenantId", filters.get("tenantId"));
        }
        if (filters.containsKey("actorId")) {
            sql.append(" AND actor_id = :actorId");
            params.addValue("actorId", filters.get("actorId"));
        }
        if (filters.containsKey("subjectId")) {
            sql.append(" AND subject_id = :subjectId");
            params.addValue("subjectId", filters.get("subjectId"));
        }
        if (filters.containsKey("action")) {
            sql.append(" AND action = :action");
            params.addValue("action", filters.get("action"));
        }
        if (filters.containsKey("outcome")) {
            sql.append(" AND outcome = :outcome");
            params.addValue("outcome", filters.get("outcome"));
        }
        if (filters.containsKey("correlationId")) {
            sql.append(" AND correlation_id = :correlationId");
            params.addValue("correlationId", filters.get("correlationId"));
        }
        if (filters.containsKey("traceId")) {
            sql.append(" AND trace_id = :traceId");
            params.addValue("traceId", filters.get("traceId"));
        }
        if (filters.containsKey("appId")) {
            sql.append(" AND app_id = :appId");
            params.addValue("appId", filters.get("appId"));
        }
        if (filters.containsKey("fromDate")) {
            sql.append(" AND occurred_at_utc >= :fromDate");
            params.addValue("fromDate", filters.get("fromDate"));
        }
        if (filters.containsKey("toDate")) {
            sql.append(" AND occurred_at_utc <= :toDate");
            params.addValue("toDate", filters.get("toDate"));
        }
        
        return jdbc.queryForObject(sql.toString(), params, Long.class);
    }
}

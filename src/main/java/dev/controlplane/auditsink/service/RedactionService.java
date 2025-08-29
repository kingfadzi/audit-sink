package dev.controlplane.auditsink.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class RedactionService {
    private final List<String> redactKeys;
    private final int maxBytes;
    private final ObjectMapper mapper = new ObjectMapper();

    public RedactionService(
            @Value("${audit.redaction.redactKeys:}") List<String> redactKeys,
            @Value("${audit.payload.maxJsonBytes:4096}") int maxBytes
    ) {
        this.redactKeys = redactKeys;
        this.maxBytes = maxBytes;
    }

    @SuppressWarnings("unchecked")
    public String redactAndCap(Map<String, Object> original) {
        if (original == null) return null;
        Map<String, Object> copy = deepCopy(original);
        maskRecursive(copy);
        try {
            String json = mapper.writeValueAsString(copy);
            if (json.getBytes().length > maxBytes) {
                return mapper.writeValueAsString(Map.of("truncated", true, "sizeBytes", json.getBytes().length));
            }
            return json;
        } catch (JsonProcessingException e) {
            return "{\"error\":\"redaction-serialization-failed\"}";
        }
    }

    @SuppressWarnings("unchecked")
    private void maskRecursive(Object node) {
        if (node instanceof Map<?,?> m) {            Map<Object, Object> mutableMap = (Map<Object, Object>) m;
            for (Map.Entry<Object, Object> e : mutableMap.entrySet()) {
                if (e.getKey() != null && redactKeys.stream().anyMatch(k -> k.equalsIgnoreCase(e.getKey().toString()))) {
                    e.setValue("***");
                } else {
                    maskRecursive(e.getValue());
                }
            }        } else if (node instanceof Iterable<?> it) {
            for (Object child : it) maskRecursive(child);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deepCopy(Map<String, Object> in) {
        Map<String, Object> out = new HashMap<>();
        for (Map.Entry<String, Object> e : in.entrySet()) {
            Object v = e.getValue();
            if (v instanceof Map<?,?> m) {
                out.put(e.getKey(), deepCopy((Map<String, Object>) m));
            } else {
                out.put(e.getKey(), v);
            }
        }
        return out;
    }
}

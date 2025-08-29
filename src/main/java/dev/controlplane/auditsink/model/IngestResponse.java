package dev.controlplane.auditsink.model;

public record IngestResponse(String eventId, boolean deduped) {}

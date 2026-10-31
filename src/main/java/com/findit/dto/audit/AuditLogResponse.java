package com.findit.dto.audit;

import java.time.Instant;

/**
 * Audit trail entry shown to administrators.
 */
public record AuditLogResponse(
        Long id,
        Long actorId,
        String action,
        String entityType,
        String entityId,
        Instant createdAt,
        String metadata
) {
}

package com.kabus.tracking.web.dto;

import java.time.LocalDateTime;

/** DTOs for the audit-log viewer. */
public final class AuditDtos {

    private AuditDtos() {
    }

    public record AuditLogResponse(
            Long id,
            String action,
            String resourceType,
            Long resourceId,
            String detailJson,
            String ipAddress,
            String userAgent,
            Long userId,
            String username,
            LocalDateTime createdAt) {
    }
}

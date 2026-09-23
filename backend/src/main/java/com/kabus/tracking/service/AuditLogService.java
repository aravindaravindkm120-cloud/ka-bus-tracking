package com.kabus.tracking.service;

import com.kabus.tracking.domain.entity.AuditLog;
import com.kabus.tracking.domain.repository.AuditLogRepository;
import com.kabus.tracking.exception.ApiException;
import com.kabus.tracking.security.UserPrincipal;
import com.kabus.tracking.web.dto.AuditDtos;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Read-only audit-log viewer. Restricted to SUPER_ADMIN. */
@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public Page<AuditDtos.AuditLogResponse> list(UserPrincipal principal, String action, String resourceType,
                                                 Long userId, LocalDateTime from, LocalDateTime to,
                                                 int page, int size) {
        if (principal == null || !principal.isSuperAdmin()) {
            throw ApiException.forbidden("Only SUPER_ADMIN may view audit logs.");
        }
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200));
        return auditLogRepository
                .search(blankToNull(action), blankToNull(resourceType), userId, from, to, pageable)
                .map(this::toResponse);
    }

    private AuditDtos.AuditLogResponse toResponse(AuditLog log) {
        return new AuditDtos.AuditLogResponse(
                log.getId(),
                log.getAction(),
                log.getResourceType(),
                log.getResourceId(),
                log.getDetailJson(),
                log.getIpAddress(),
                log.getUserAgent(),
                log.getUser() == null ? null : log.getUser().getId(),
                log.getUser() == null ? null : log.getUser().getUsername(),
                log.getCreatedAt());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

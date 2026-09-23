package com.kabus.tracking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kabus.tracking.domain.entity.AuditLog;
import com.kabus.tracking.domain.entity.User;
import com.kabus.tracking.domain.repository.AuditLogRepository;
import com.kabus.tracking.domain.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Asynchronous audit-log writer. Failures are logged but never block the
 * business operation being audited.
 *
 * <p>Callers should pass the authenticated actor's user id so the audit trail
 * records <em>who</em> performed the mutation. Passwords, tokens and other
 * secrets must never be placed in {@code detail}.</p>
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository auditLogRepository,
                        UserRepository userRepository,
                        ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Async
    public void record(User user, String action, String resourceType, Long resourceId,
                       Object detail, String ip, String userAgent) {
        write(user, action, resourceType, resourceId, detail, ip, userAgent);
    }

    /** Preferred entry point: identifies the actor by id (no entity needed by the caller). */
    @Async
    public void record(Long actorUserId, String action, String resourceType, Long resourceId,
                       Object detail, String ip, String userAgent) {
        User user = null;
        if (actorUserId != null) {
            try {
                user = userRepository.findById(actorUserId).orElse(null);
            } catch (Exception e) {
                log.warn("Audit actor lookup failed for user {}: {}", actorUserId, e.getMessage());
            }
        }
        write(user, action, resourceType, resourceId, detail, ip, userAgent);
    }

    private void write(User user, String action, String resourceType, Long resourceId,
                       Object detail, String ip, String userAgent) {
        try {
            AuditLog entry = new AuditLog();
            entry.setUser(user);
            entry.setAction(action);
            entry.setResourceType(resourceType);
            entry.setResourceId(resourceId);
            if (detail != null) {
                entry.setDetailJson(objectMapper.writeValueAsString(detail));
            }
            entry.setIpAddress(ip);
            entry.setUserAgent(userAgent == null || userAgent.length() <= 255 ? userAgent : userAgent.substring(0, 255));
            auditLogRepository.save(entry);
        } catch (Exception e) {
            log.warn("Failed to write audit log for action {}: {}", action, e.getMessage());
        }
    }
}

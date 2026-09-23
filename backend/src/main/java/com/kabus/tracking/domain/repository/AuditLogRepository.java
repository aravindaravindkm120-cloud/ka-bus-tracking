package com.kabus.tracking.domain.repository;

import com.kabus.tracking.domain.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("select a from AuditLog a where "
            + "(:action is null or a.action = :action) "
            + "and (:resourceType is null or a.resourceType = :resourceType) "
            + "and (:userId is null or a.user.id = :userId) "
            + "and (:from is null or a.createdAt >= :from) "
            + "and (:to is null or a.createdAt <= :to) "
            + "order by a.createdAt desc")
    Page<AuditLog> search(@Param("action") String action,
                          @Param("resourceType") String resourceType,
                          @Param("userId") Long userId,
                          @Param("from") LocalDateTime from,
                          @Param("to") LocalDateTime to,
                          Pageable pageable);
}

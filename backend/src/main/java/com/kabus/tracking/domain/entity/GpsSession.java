package com.kabus.tracking.domain.entity;

import com.kabus.tracking.domain.enums.GpsSessionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "gps_sessions")
@Getter
@Setter
@NoArgsConstructor
public class GpsSession extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bus_id", nullable = false)
    private Bus bus;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "crew_id", nullable = false)
    private Crew crew;

    @Column(name = "session_key", nullable = false, length = 36, unique = true)
    private String sessionKey = UUID.randomUUID().toString();

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt = LocalDateTime.now();

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private GpsSessionStatus status = GpsSessionStatus.ACTIVE;

    @Column(name = "start_latitude", precision = 8, scale = 6)
    private BigDecimal startLatitude;

    @Column(name = "start_longitude", precision = 9, scale = 6)
    private BigDecimal startLongitude;

    @Column(name = "device_id", length = 80)
    private String deviceId;

    @Column(name = "app_version", length = 20)
    private String appVersion;

    @Column(name = "last_heartbeat_at")
    private LocalDateTime lastHeartbeatAt;

    @Column(name = "updates_count", nullable = false)
    private long updatesCount = 0;
}
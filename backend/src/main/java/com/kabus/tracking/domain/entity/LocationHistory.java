package com.kabus.tracking.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "location_history")
@Getter
@Setter
@NoArgsConstructor
public class LocationHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bus_id", nullable = false)
    private Bus bus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id")
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gps_session_id")
    private GpsSession gpsSession;

    @Column(name = "latitude", nullable = false, precision = 8, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = false, precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "speed_kmh", nullable = false, precision = 6, scale = 2)
    private BigDecimal speedKmh = BigDecimal.ZERO;

    @Column(name = "heading", nullable = false, precision = 6, scale = 2)
    private BigDecimal heading = BigDecimal.ZERO;

    @Column(name = "accuracy_m", nullable = false, precision = 7, scale = 2)
    private BigDecimal accuracyM = BigDecimal.ZERO;

    @Column(name = "altitude_m", precision = 8, scale = 2)
    private BigDecimal altitudeM;

    @Column(name = "captured_at", nullable = false)
    private LocalDateTime capturedAt;
}
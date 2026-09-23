package com.kabus.tracking.ws;

import com.kabus.tracking.domain.enums.LiveStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Public live-bus message broadcast on <code>/topic/live</code>.
 *
 * <p>Contains only bus/trip/route/location/status - never private crew
 * information. Status is always computed by the backend (LiveStatusPolicy),
 * never supplied by the client.</p>
 */
public record LiveLocationMessage(
        Long busId,
        Long tripId,
        Long routeId,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal speedKmh,
        BigDecimal heading,
        BigDecimal accuracyM,
        BigDecimal altitudeM,
        Instant capturedAt,
        LiveStatus status
) {

    @Override
    public String toString() {
        return "LiveLocationMessage{busId=" + busId + ", status=" + status + "}";
    }
}
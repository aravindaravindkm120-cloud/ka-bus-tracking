package com.kabus.tracking.web.dto;

import com.kabus.tracking.domain.enums.LiveStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** DTOs for the public (unauthenticated) passenger API. */
public final class PassengerDtos {

    private PassengerDtos() {
    }

    public record PublicConfig(
            int liveSeconds,
            int staleSeconds,
            int liveThresholdSeconds,
            int staleThresholdSeconds,
            int mapBusCap,
            boolean adsEnabled) {
    }

    public record StopDto(
            int order,
            String name,
            BigDecimal latitude,
            BigDecimal longitude,
            BigDecimal distanceFromStart) {
    }

    public record RouteDto(
            Long routeId,
            String code,
            String name,
            String origin,
            String destination,
            String direction,
            List<StopDto> stops) {
    }

    /** GeoJSON LineString geometry for an OSRM road route. */
    public record GeometryLineStringDto(String type, List<List<Double>> coordinates) {
    }

    /**
     * Road-network route geometry for a set of ordered route stops. The geometry
     * is produced by OSRM from the actual road network and cached centrally so
     * many passengers reuse a single OSRM request.
     */
    public record RouteGeometryDto(
            Long routeId,
            String code,
            String stopsKey,
            String source,
            boolean available,
            String reason,
            GeometryLineStringDto geometry,
            Double distanceKm,
            Double durationSec,
            LocalDateTime computedAt) {
    }

    public record BusSummary(
            Long busId,
            String registrationNo,
            String busType,
            Long routeId,
            String routeName,
            String routeCode,
            String direction,
            String origin,
            String destination,
            BigDecimal latitude,
            BigDecimal longitude,
            BigDecimal speedKmh,
            BigDecimal heading,
            BigDecimal accuracyM,
            LiveStatus status,
            LocalDateTime lastUpdate) {
    }

    public record SearchResult(
            String from,
            String to,
            String direction,
            long total,
            List<BusSummary> buses,
            List<RouteDto> routes,
            List<ConnectingJourney> connecting) {

        /** Backward-compatible constructor: no connecting journeys. */
        public SearchResult(String from, String to, String direction, long total,
                            List<BusSummary> buses, List<RouteDto> routes) {
            this(from, to, direction, total, buses, routes, List.of());
        }
    }

    /**
     * One leg of a connecting journey: a real ride over one route in one
     * direction. {@code route} is the corridor slice (real ordered stops in
     * travel order) so the frontend can render the road geometry for each leg.
     * {@code buses} are the currently available buses/trips for this leg only.
     */
    public record JourneyLeg(
            int legNumber,
            String journeyType,
            String from,
            String to,
            String transferStop,
            LocalDateTime plannedDeparture,
            LocalDateTime plannedArrival,
            RouteDto route,
            List<BusSummary> buses) {
    }

    /**
     * A multi-route connecting journey built from the real route-stop graph.
     * numberOfTransfers is the number of en-route changes (0 = direct, but the
     * {@code connecting} list only ever contains transfers >= 1; direct
     * results stay in SearchResult.routes/buses). transferStops are the real
     * shared stops where the passenger changes routes.
     */
    public record ConnectingJourney(
            String from,
            String to,
            String journeyType,
            int numberOfTransfers,
            List<String> transferStops,
            List<JourneyLeg> legs) {
    }

    /** A single stop matched by the passenger FROM/TO autocomplete. */
    public record StopSuggestion(
            String name,
            BigDecimal latitude,
            BigDecimal longitude) {
    }

    public record BusDetails(
            Long busId,
            String registrationNo,
            String busType,
            String status,
            Long tripId,
            String tripNumber,
            RouteDto route,
            StopDto nextStop,
            BigDecimal nextStopDistanceKm,
            BigDecimal latitude,
            BigDecimal longitude,
            BigDecimal speedKmh,
            BigDecimal heading,
            BigDecimal accuracyM,
            BigDecimal altitudeM,
            LiveStatus liveStatus,
            LocalDateTime lastUpdate) {
    }

    public record NearbyResult(List<BusSummary> buses) {
    }

    public record SimpleMessage(String message) {
    }

    public record SearchRequest(
            @jakarta.validation.constraints.NotBlank String from,
            @jakarta.validation.constraints.NotBlank String to) {
    }

    public record RecentSearchItem(String from, String to, LocalDateTime searchedAt) {
    }

    public record FavoriteItem(
            Long favoriteId,
            String itemType,
            Long itemId,
            String label,
            BigDecimal latitude,
            BigDecimal longitude) {
    }
}
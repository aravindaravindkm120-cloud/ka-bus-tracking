package com.kabus.tracking.service;

import java.util.List;
import java.util.Optional;

/**
 * Pluggable source of road-network route geometry. The production
 * implementation uses OSRM; this seam keeps the routing layer replaceable
 * (e.g. a dedicated routing service) without touching the rest of the app.
 */
public interface RouteGeometryClient {

    /** An ordered route stop with a real coordinate. */
    record Point(double lat, double lon) {
    }

    /** Complete road path for an ordered list of stops. */
    record RouteResult(List<double[]> coordinates, int points, double distanceMeters, double durationSeconds) {
    }

    /**
     * Compute the road-network path through the given ordered stops,
     * following the real road network between consecutive stops.
     *
     * @return an empty Optional when routing could not be determined; the
     *         caller must never substitute fake geometry for it.
     */
    Optional<RouteResult> route(List<Point> orderedStops);
}
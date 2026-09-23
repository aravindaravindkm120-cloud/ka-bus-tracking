package com.kabus.tracking.service;

import com.kabus.tracking.domain.entity.Division;
import com.kabus.tracking.domain.entity.Route;
import com.kabus.tracking.domain.entity.RouteStop;
import com.kabus.tracking.domain.repository.DivisionRepository;
import com.kabus.tracking.domain.repository.RouteRepository;
import com.kabus.tracking.domain.repository.RouteStopRepository;
import com.kabus.tracking.exception.ApiException;
import com.kabus.tracking.security.UserPrincipal;
import com.kabus.tracking.web.dto.PassengerDtos;
import com.kabus.tracking.web.dto.RouteDtos;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Route + route-stop management.
 *
 * <p>Routes are division-level. SUPER_ADMIN and DIVISION_ADMIN/MANAGER may
 * mutate them within their division; DEPOT_HEAD/TOWN_MANAGER have read access
 * to routes in their division but cannot mutate division-level data. Stops are
 * validated against the real road geometry (OSRM) before being saved, and the
 * geometry cache is content-addressed by the stop fingerprint, so editing stops
 * automatically produces a new cache entry.</p>
 */
@Service
public class RouteService {

    private final RouteRepository routeRepository;
    private final RouteStopRepository routeStopRepository;
    private final DivisionRepository divisionRepository;
    private final RouteGeometryService routeGeometryService;
    private final ScopeResolver scopeResolver;
    private final ScopeGuard scopeGuard;
    private final AuditService auditService;

    public RouteService(RouteRepository routeRepository,
                        RouteStopRepository routeStopRepository,
                        DivisionRepository divisionRepository,
                        RouteGeometryService routeGeometryService,
                        ScopeResolver scopeResolver,
                        ScopeGuard scopeGuard,
                        AuditService auditService) {
        this.routeRepository = routeRepository;
        this.routeStopRepository = routeStopRepository;
        this.divisionRepository = divisionRepository;
        this.routeGeometryService = routeGeometryService;
        this.scopeResolver = scopeResolver;
        this.scopeGuard = scopeGuard;
        this.auditService = auditService;
    }

    // ------------------------------------------------------------------
    // Reads
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<RouteDtos.RouteResponse> list(UserPrincipal principal, String search, int page, int size) {
        AccessScope scope = scopeResolver.resolve(principal);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        String term = (search == null || search.isBlank()) ? null : search.trim();

        Page<Route> routes;
        if (scope.wholeSystem()) {
            routes = term == null ? routeRepository.findAll(pageable) : routeRepository.searchAll(term, pageable);
        } else {
            List<Long> divisionIds = scopeResolver.divisionIds(scope);
            if (divisionIds.isEmpty()) {
                return Page.empty(pageable);
            }
            routes = term == null
                    ? routeRepository.findByDivisionIdIn(divisionIds, pageable)
                    : routeRepository.searchInDivisions(divisionIds, term, pageable);
        }

        Map<Long, Integer> counts = stopCounts(routes.getContent());
        return routes.map(r -> toResponse(r, null, counts.getOrDefault(r.getId(), 0)));
    }

    @Transactional(readOnly = true)
    public RouteDtos.RouteResponse get(UserPrincipal principal, Long id) {
        AccessScope scope = scopeResolver.resolve(principal);
        Route route = load(id);
        requireReadable(scope, route);
        List<RouteStop> stops = routeStopRepository.findByRouteIdOrderByStopOrderAsc(id);
        return toResponse(route, stops, stops.size());
    }

    @Transactional(readOnly = true)
    public PassengerDtos.RouteGeometryDto geometry(UserPrincipal principal, Long id) {
        AccessScope scope = scopeResolver.resolve(principal);
        Route route = load(id);
        requireReadable(scope, route);
        return routeGeometryService.geometry(id);
    }

    @Transactional(readOnly = true)
    public RouteDtos.PreviewResponse preview(UserPrincipal principal, RouteDtos.PreviewRequest req) {
        AccessScope scope = scopeResolver.resolve(principal);
        if (!scope.wholeSystem() && scope.corporationId() == null && scope.divisionId() == null) {
            throw ApiException.forbidden("Account has no division scope.");
        }
        RouteGeometryService.GeometryPreview preview = routeGeometryService.preview(toPoints(req.stops()));
        return new RouteDtos.PreviewResponse(
                preview.available(),
                preview.reason(),
                preview.coordinates() == null
                        ? null
                        : new PassengerDtos.GeometryLineStringDto("LineString", preview.coordinates()),
                preview.distanceKm(),
                preview.durationSec());
    }

    // ------------------------------------------------------------------
    // Writes
    // ------------------------------------------------------------------

    @Transactional
    public RouteDtos.RouteResponse create(UserPrincipal principal, RouteDtos.CreateRouteRequest req,
                                          HttpServletRequest http) {
        AccessScope scope = scopeResolver.resolve(principal);
        Long divisionId = req.divisionId();
        if (divisionId == null) {
            if (scope.wholeSystem() || scope.divisionId() == null) {
                throw ApiException.badRequest("divisionId is required.");
            }
            divisionId = scope.divisionId();
        }
        scopeGuard.requireWithin(scope, divisionId, null, null);
        final Long divisionIdForLookup = divisionId;
        Division division = divisionRepository.findById(divisionIdForLookup)
                .orElseThrow(() -> ApiException.badRequest("Division not found: " + divisionIdForLookup));

        String code = req.code().trim().toUpperCase();
        if (routeRepository.existsByCodeIgnoreCase(code)) {
            throw ApiException.conflict("Route code '" + code + "' already exists.");
        }

        Route route = new Route();
        route.setDivision(division);
        route.setCode(code);
        route.setName(req.name().trim());
        route.setOrigin(req.origin().trim());
        route.setDestination(req.destination().trim());
        route.setDistanceKm(req.distanceKm());
        route.setEstDurationMin(req.estDurationMin());
        route.setStatus(statusOrDefault(req.status()));
        route.setEnabled(true);
        routeRepository.save(route);

        audit(principal, http, "ROUTE_CREATE", route.getId(),
                Map.of("code", route.getCode(), "divisionId", division.getId()));
        return toResponse(route, List.of(), 0);
    }

    @Transactional
    public RouteDtos.RouteResponse update(UserPrincipal principal, Long id, RouteDtos.UpdateRouteRequest req,
                                          HttpServletRequest http) {
        AccessScope scope = scopeResolver.resolve(principal);
        Route route = load(id);
        scopeGuard.requireWithin(scope, route.getDivision().getId(), null, null);

        route.setName(req.name().trim());
        route.setOrigin(req.origin().trim());
        route.setDestination(req.destination().trim());
        route.setDistanceKm(req.distanceKm());
        route.setEstDurationMin(req.estDurationMin());
        route.setStatus(statusOrDefault(req.status()));
        routeRepository.save(route);

        audit(principal, http, "ROUTE_UPDATE", route.getId(), Map.of("code", route.getCode()));
        List<RouteStop> stops = routeStopRepository.findByRouteIdOrderByStopOrderAsc(id);
        return toResponse(route, stops, stops.size());
    }

    @Transactional
    public RouteDtos.RouteResponse setEnabled(UserPrincipal principal, Long id, boolean enabled,
                                              HttpServletRequest http) {
        AccessScope scope = scopeResolver.resolve(principal);
        Route route = load(id);
        scopeGuard.requireWithin(scope, route.getDivision().getId(), null, null);
        route.setEnabled(enabled);
        routeRepository.save(route);
        audit(principal, http, enabled ? "ROUTE_ENABLE" : "ROUTE_DISABLE", route.getId(), Map.of("enabled", enabled));
        List<RouteStop> stops = routeStopRepository.findByRouteIdOrderByStopOrderAsc(id);
        return toResponse(route, stops, stops.size());
    }

    @Transactional
    public RouteDtos.RouteResponse replaceStops(UserPrincipal principal, Long id, RouteDtos.ReplaceStopsRequest req,
                                                HttpServletRequest http) {
        AccessScope scope = scopeResolver.resolve(principal);
        Route route = load(id);
        scopeGuard.requireWithin(scope, route.getDivision().getId(), null, null);

        List<RouteDtos.StopInput> inputs = new ArrayList<>(req.stops());
        inputs.sort(Comparator.comparing(RouteDtos.StopInput::stopOrder));
        Set<Integer> orders = new HashSet<>();
        for (RouteDtos.StopInput input : inputs) {
            if (!orders.add(input.stopOrder())) {
                throw ApiException.badRequest("Duplicate stop order: " + input.stopOrder());
            }
        }

        // Validate against real road geometry before persisting. If routing is
        // unavailable we still allow the save (data entry must not be blocked by
        // a transient OSRM outage) but a definitively off-route stop is rejected.
        RouteGeometryService.GeometryPreview preview = routeGeometryService.preview(toPoints(inputs));
        if (!preview.available() && "stop-off-route".equals(preview.reason())) {
            throw ApiException.badRequest("One or more stops are too far from the road network. "
                    + "Check the coordinates so the route follows real roads.");
        }

        routeStopRepository.deleteByRouteId(id);
        routeStopRepository.flush();
        List<RouteStop> saved = new ArrayList<>(inputs.size());
        for (RouteDtos.StopInput input : inputs) {
            RouteStop stop = new RouteStop();
            stop.setRoute(route);
            stop.setStopOrder(input.stopOrder());
            stop.setStopName(input.stopName().trim());
            stop.setLatitude(input.latitude());
            stop.setLongitude(input.longitude());
            stop.setDistanceFromStart(input.distanceFromStart());
            saved.add(stop);
        }
        routeStopRepository.saveAll(saved);

        if (route.getDistanceKm() == null && preview.available()) {
            route.setDistanceKm(java.math.BigDecimal.valueOf(preview.distanceKm()).setScale(2, java.math.RoundingMode.HALF_UP));
        }
        if (route.getEstDurationMin() == null && preview.available() && preview.durationSec() != null) {
            route.setEstDurationMin((int) Math.max(1, Math.round(preview.durationSec() / 60.0)));
        }
        routeRepository.save(route);

        audit(principal, http, "ROUTE_STOPS_REPLACE", route.getId(),
                Map.of("stopCount", saved.size(), "geometryAvailable", preview.available(),
                        "geometryReason", preview.reason()));
        return toResponse(route, saved, saved.size());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Route load(Long id) {
        return routeRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Route not found: " + id));
    }

    private void requireReadable(AccessScope scope, Route route) {
        if (scope.wholeSystem()) {
            return;
        }
        if (!scopeGuard.visible(scope, route.getDivision().getId(), null, null)) {
            throw ApiException.forbidden("Route is outside your scope.");
        }
    }

    private Map<Long, Integer> stopCounts(List<Route> routes) {
        if (routes.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = routes.stream().map(Route::getId).toList();
        Map<Long, Integer> counts = new HashMap<>();
        for (Object[] row : routeStopRepository.countByRouteIds(ids)) {
            counts.put((Long) row[0], ((Number) row[1]).intValue());
        }
        return counts;
    }

    private static List<RouteGeometryClient.Point> toPoints(List<RouteDtos.StopInput> stops) {
        List<RouteGeometryClient.Point> points = new ArrayList<>(stops.size());
        for (RouteDtos.StopInput s : stops) {
            points.add(new RouteGeometryClient.Point(s.latitude().doubleValue(), s.longitude().doubleValue()));
        }
        return points;
    }

    private static String statusOrDefault(String status) {
        return status == null || status.isBlank() ? "ACTIVE" : status.trim().toUpperCase();
    }

    private RouteDtos.RouteResponse toResponse(Route route, List<RouteStop> stops, int stopCount) {
        List<RouteDtos.RouteStopResponse> stopDtos = stops == null ? null : stops.stream()
                .map(s -> new RouteDtos.RouteStopResponse(s.getId(), s.getStopOrder(), s.getStopName(),
                        s.getLatitude(), s.getLongitude(), s.getDistanceFromStart()))
                .toList();
        return new RouteDtos.RouteResponse(
                route.getId(),
                route.getCode(),
                route.getName(),
                route.getOrigin(),
                route.getDestination(),
                route.getDistanceKm(),
                route.getEstDurationMin(),
                route.getStatus(),
                route.isEnabled(),
                route.getDivision().getId(),
                route.getDivision().getName(),
                route.getDivision().getCorporation() == null ? null : route.getDivision().getCorporation().getName(),
                stopCount,
                stopDtos,
                route.getCreatedAt(),
                route.getUpdatedAt());
    }

    private void audit(UserPrincipal principal, HttpServletRequest http, String action, Long id, Object detail) {
        auditService.record(principal == null ? null : principal.getUserId(), action, "ROUTE", id, detail,
                http == null ? null : http.getRemoteAddr(),
                http == null ? null : http.getHeader("User-Agent"));
    }
}

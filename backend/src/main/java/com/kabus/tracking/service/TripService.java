package com.kabus.tracking.service;

import com.kabus.tracking.domain.entity.Bus;
import com.kabus.tracking.domain.entity.BusAssignment;
import com.kabus.tracking.domain.entity.Crew;
import com.kabus.tracking.domain.entity.CrewAssignment;
import com.kabus.tracking.domain.entity.Route;
import com.kabus.tracking.domain.entity.Trip;
import com.kabus.tracking.domain.enums.TripStatus;
import com.kabus.tracking.domain.repository.BusAssignmentRepository;
import com.kabus.tracking.domain.repository.BusRepository;
import com.kabus.tracking.domain.repository.CrewAssignmentRepository;
import com.kabus.tracking.domain.repository.CrewRepository;
import com.kabus.tracking.domain.repository.DepotRepository;
import com.kabus.tracking.domain.repository.RouteRepository;
import com.kabus.tracking.domain.repository.TripRepository;
import com.kabus.tracking.exception.ApiException;
import com.kabus.tracking.security.UserPrincipal;
import com.kabus.tracking.web.dto.TripDtos;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Trip scheduling and bus/crew assignment, restricted to the caller's
 * server-resolved scope (via the trip's bus depot).
 */
@Service
public class TripService {

    private final TripRepository tripRepository;
    private final RouteRepository routeRepository;
    private final BusRepository busRepository;
    private final DepotRepository depotRepository;
    private final CrewRepository crewRepository;
    private final CrewAssignmentRepository crewAssignmentRepository;
    private final BusAssignmentRepository busAssignmentRepository;
    private final ScopeResolver scopeResolver;
    private final ScopeGuard scopeGuard;
    private final AuditService auditService;

    public TripService(TripRepository tripRepository,
                       RouteRepository routeRepository,
                       BusRepository busRepository,
                       DepotRepository depotRepository,
                       CrewRepository crewRepository,
                       CrewAssignmentRepository crewAssignmentRepository,
                       BusAssignmentRepository busAssignmentRepository,
                       ScopeResolver scopeResolver,
                       ScopeGuard scopeGuard,
                       AuditService auditService) {
        this.tripRepository = tripRepository;
        this.routeRepository = routeRepository;
        this.busRepository = busRepository;
        this.depotRepository = depotRepository;
        this.crewRepository = crewRepository;
        this.crewAssignmentRepository = crewAssignmentRepository;
        this.busAssignmentRepository = busAssignmentRepository;
        this.scopeResolver = scopeResolver;
        this.scopeGuard = scopeGuard;
        this.auditService = auditService;
    }

    // ------------------------------------------------------------------
    // Reads
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<TripDtos.TripResponse> list(UserPrincipal principal, LocalDate date, TripStatus status,
                                            String search, int page, int size) {
        AccessScope scope = scopeResolver.resolve(principal);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        String term = (search == null || search.isBlank()) ? null : search.trim();
        if (scope.townId() != null) {
            return tripRepository.searchByTownIds(List.of(scope.townId()), date, status, term, pageable)
                    .map(t -> toResponse(t, false));
        }
        List<Long> depotIds = scopeResolver.depotIds(scope);
        if (depotIds.isEmpty()) {
            return Page.empty(pageable);
        }
        return tripRepository.search(depotIds, date, status, term, pageable).map(t -> toResponse(t, false));
    }

    @Transactional(readOnly = true)
    public TripDtos.TripResponse get(UserPrincipal principal, Long id) {
        AccessScope scope = scopeResolver.resolve(principal);
        Trip trip = load(id);
        requireTripInScope(scope, trip);
        return toResponse(trip, true);
    }

    // ------------------------------------------------------------------
    // Writes
    // ------------------------------------------------------------------

    @Transactional
    public TripDtos.TripResponse create(UserPrincipal principal, TripDtos.CreateTripRequest req,
                                        HttpServletRequest http) {
        AccessScope scope = scopeResolver.resolve(principal);
        Route route = routeRepository.findById(req.routeId())
                .orElseThrow(() -> ApiException.badRequest("Route not found: " + req.routeId()));
        Bus bus = busRepository.findById(req.busId())
                .orElseThrow(() -> ApiException.badRequest("Bus not found: " + req.busId()));
        requireBusMatchesRoute(bus, route);
        scopeGuard.requireWithin(scope, route.getDivision().getId(), bus.getDepot().getId(), bus.getTown().getId());
        validateTimes(req.scheduledDeparture(), req.scheduledArrival());

        String tripNumber = (req.tripNumber() == null || req.tripNumber().isBlank())
                ? generateTripNumber(route, req.tripDate())
                : req.tripNumber().trim();
        if (!tripRepository.findByTripNumberAndTripDate(tripNumber, req.tripDate()).isEmpty()) {
            throw ApiException.conflict("Trip number '" + tripNumber + "' already exists on " + req.tripDate() + ".");
        }

        Trip trip = new Trip();
        trip.setRoute(route);
        trip.setBus(bus);
        trip.setTripNumber(tripNumber);
        trip.setTripDate(req.tripDate());
        trip.setScheduledDeparture(req.scheduledDeparture());
        trip.setScheduledArrival(req.scheduledArrival());
        trip.setDirection(req.direction() == null || req.direction().isBlank()
                ? "OUTBOUND" : req.direction().trim().toUpperCase());
        trip.setStatus(req.status() == null ? TripStatus.SCHEDULED : req.status());
        tripRepository.save(trip);

        audit(principal, http, "TRIP_CREATE", trip.getId(),
                Map.of("tripNumber", trip.getTripNumber(), "routeId", route.getId(), "busId", bus.getId()));
        return toResponse(trip, true);
    }

    @Transactional
    public TripDtos.TripResponse update(UserPrincipal principal, Long id, TripDtos.UpdateTripRequest req,
                                        HttpServletRequest http) {
        AccessScope scope = scopeResolver.resolve(principal);
        Trip trip = load(id);
        requireTripInScope(scope, trip);
        Bus bus = busRepository.findById(req.busId())
                .orElseThrow(() -> ApiException.badRequest("Bus not found: " + req.busId()));
        requireBusMatchesRoute(bus, trip.getRoute());
        scopeGuard.requireWithin(scope, trip.getRoute().getDivision().getId(),
                bus.getDepot().getId(), bus.getTown().getId());
        validateTimes(req.scheduledDeparture(), req.scheduledArrival());

        trip.setBus(bus);
        trip.setTripDate(req.tripDate());
        trip.setScheduledDeparture(req.scheduledDeparture());
        trip.setScheduledArrival(req.scheduledArrival());
        if (req.direction() != null && !req.direction().isBlank()) {
            trip.setDirection(req.direction().trim().toUpperCase());
        }
        tripRepository.save(trip);

        audit(principal, http, "TRIP_UPDATE", trip.getId(), Map.of("tripNumber", trip.getTripNumber()));
        return toResponse(trip, true);
    }

    @Transactional
    public TripDtos.TripResponse setStatus(UserPrincipal principal, Long id, String status,
                                           HttpServletRequest http) {
        AccessScope scope = scopeResolver.resolve(principal);
        Trip trip = load(id);
        requireTripInScope(scope, trip);
        TripStatus next;
        try {
            next = TripStatus.valueOf(status == null ? "" : status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Invalid trip status: " + status);
        }
        trip.setStatus(next);
        tripRepository.save(trip);
        audit(principal, http, "TRIP_STATUS_CHANGE", trip.getId(), Map.of("status", next.name()));
        return toResponse(trip, true);
    }

    @Transactional
    public TripDtos.TripResponse assignBus(UserPrincipal principal, Long id, TripDtos.AssignBusRequest req,
                                           HttpServletRequest http) {
        AccessScope scope = scopeResolver.resolve(principal);
        Trip trip = load(id);
        requireTripInScope(scope, trip);
        Bus bus = busRepository.findById(req.busId())
                .orElseThrow(() -> ApiException.badRequest("Bus not found: " + req.busId()));
        requireBusMatchesRoute(bus, trip.getRoute());
        scopeGuard.requireWithin(scope, trip.getRoute().getDivision().getId(),
                bus.getDepot().getId(), bus.getTown().getId());

        retireActiveBusAssignments(id);
        BusAssignment assignment = new BusAssignment();
        assignment.setTrip(trip);
        assignment.setBus(bus);
        assignment.setAssignedFrom(LocalDateTime.now());
        assignment.setStatus("ACTIVE");
        busAssignmentRepository.save(assignment);

        trip.setBus(bus);
        tripRepository.save(trip);

        audit(principal, http, "TRIP_BUS_ASSIGN", trip.getId(), Map.of("busId", bus.getId()));
        return toResponse(trip, true);
    }

    @Transactional
    public TripDtos.TripResponse unassignBus(UserPrincipal principal, Long id, HttpServletRequest http) {
        AccessScope scope = scopeResolver.resolve(principal);
        Trip trip = load(id);
        requireTripInScope(scope, trip);
        retireActiveBusAssignments(id);
        audit(principal, http, "TRIP_BUS_UNASSIGN", trip.getId(), Map.of("tripNumber", trip.getTripNumber()));
        return toResponse(trip, true);
    }

    @Transactional
    public TripDtos.TripResponse assignCrew(UserPrincipal principal, Long id, TripDtos.AssignCrewRequest req,
                                            HttpServletRequest http) {
        AccessScope scope = scopeResolver.resolve(principal);
        Trip trip = load(id);
        requireTripInScope(scope, trip);
        Crew crew = crewRepository.findById(req.crewId())
                .orElseThrow(() -> ApiException.badRequest("Crew not found: " + req.crewId()));
        Long crewDepotId = crewDepotId(crew);
        if (crewDepotId == null) {
            // Crew with no organizational link is not visible to a scoped admin;
            // whole-system callers (SUPER_ADMIN) may still assign it.
            scopeGuard.requireWithin(scope, null, null, null);
        } else {
            if (!crewDepotId.equals(trip.getBus().getDepot().getId())) {
                throw ApiException.badRequest("Crew " + crew.getId() + " belongs to a different depot than the trip bus.");
            }
            // The crew's effective town and division must also fall inside the
            // caller's scope. For a TOWN_MANAGER this rejects a crew from a
            // different town even when both share the same depot.
            scopeGuard.requireWithin(scope, crewDivisionId(crew), crewDepotId, crewTownId(crew));
        }
        String crewType = crew.getCrewType() == null ? "CREW" : crew.getCrewType().trim().toUpperCase();

        crewAssignmentRepository.findFirstByTripIdAndCrewTypeAndStatus(id, crewType, "ACTIVE")
                .ifPresent(existing -> {
                    existing.setStatus("INACTIVE");
                    existing.setAssignedTo(LocalDateTime.now());
                    crewAssignmentRepository.save(existing);
                });

        CrewAssignment assignment = new CrewAssignment();
        assignment.setTrip(trip);
        assignment.setCrew(crew);
        assignment.setCrewType(crewType);
        assignment.setAssignedFrom(LocalDateTime.now());
        assignment.setStatus("ACTIVE");
        crewAssignmentRepository.save(assignment);

        audit(principal, http, "TRIP_CREW_ASSIGN", trip.getId(),
                Map.of("crewId", crew.getId(), "crewType", crewType));
        return toResponse(trip, true);
    }

    @Transactional
    public TripDtos.TripResponse unassignCrew(UserPrincipal principal, Long id, Long crewId,
                                              HttpServletRequest http) {
        AccessScope scope = scopeResolver.resolve(principal);
        Trip trip = load(id);
        requireTripInScope(scope, trip);
        boolean removed = false;
        for (CrewAssignment assignment : crewAssignmentRepository.findByTripIdAndStatus(id, "ACTIVE")) {
            if (assignment.getCrew().getId().equals(crewId)) {
                assignment.setStatus("INACTIVE");
                assignment.setAssignedTo(LocalDateTime.now());
                crewAssignmentRepository.save(assignment);
                removed = true;
            }
        }
        if (!removed) {
            throw ApiException.notFound("No active assignment for crew " + crewId + " on trip " + id + ".");
        }
        audit(principal, http, "TRIP_CREW_UNASSIGN", trip.getId(), Map.of("crewId", crewId));
        return toResponse(trip, true);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Trip load(Long id) {
        return tripRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Trip not found: " + id));
    }

    private void requireTripInScope(AccessScope scope, Trip trip) {
        Bus bus = trip.getBus();
        scopeGuard.requireWithin(scope, bus.getDepot().getDivision().getId(),
                bus.getDepot().getId(), bus.getTown().getId());
    }

    private void requireBusMatchesRoute(Bus bus, Route route) {
        if (!bus.getDepot().getDivision().getId().equals(route.getDivision().getId())) {
            throw ApiException.badRequest("Bus and route belong to different divisions.");
        }
    }

    private void validateTimes(LocalDateTime departure, LocalDateTime arrival) {
        if (arrival.isBefore(departure)) {
            throw ApiException.badRequest("Scheduled arrival cannot be before scheduled departure.");
        }
    }

    private void retireActiveBusAssignments(Long tripId) {
        for (BusAssignment assignment : busAssignmentRepository.findByTripIdAndStatus(tripId, "ACTIVE")) {
            assignment.setStatus("INACTIVE");
            assignment.setAssignedTo(LocalDateTime.now());
            busAssignmentRepository.save(assignment);
        }
    }

    private Long crewDepotId(Crew crew) {
        if (crew.getStaff() != null && crew.getStaff().getDepot() != null) {
            return crew.getStaff().getDepot().getId();
        }
        if (crew.getUser() != null && crew.getUser().getDepot() != null) {
            return crew.getUser().getDepot().getId();
        }
        return null;
    }

    private Long crewDivisionId(Crew crew) {
        if (crew.getStaff() != null && crew.getStaff().getDivision() != null) {
            return crew.getStaff().getDivision().getId();
        }
        if (crew.getUser() != null && crew.getUser().getDivision() != null) {
            return crew.getUser().getDivision().getId();
        }
        Long depotId = crewDepotId(crew);
        if (depotId == null) {
            return null;
        }
        return depotRepository.findById(depotId)
                .map(d -> d.getDivision().getId())
                .orElse(null);
    }

    private Long crewTownId(Crew crew) {
        if (crew.getStaff() != null && crew.getStaff().getTown() != null) {
            return crew.getStaff().getTown().getId();
        }
        if (crew.getUser() != null && crew.getUser().getTown() != null) {
            return crew.getUser().getTown().getId();
        }
        return null;
    }

    private String generateTripNumber(Route route, LocalDate date) {
        String base = route.getCode() + "-" + date.format(DateTimeFormatter.BASIC_ISO_DATE);
        long seq = tripRepository.countByRouteIdAndTripDate(route.getId(), date) + 1;
        String candidate = base + "-" + String.format("%03d", seq);
        while (!tripRepository.findByTripNumberAndTripDate(candidate, date).isEmpty()) {
            seq++;
            candidate = base + "-" + String.format("%03d", seq);
        }
        return candidate;
    }

    private TripDtos.TripResponse toResponse(Trip trip, boolean includeAssignments) {
        Bus bus = trip.getBus();
        Route route = trip.getRoute();
        List<TripDtos.CrewAssignmentResponse> crewAssignments = null;
        List<TripDtos.BusAssignmentResponse> busAssignments = null;
        if (includeAssignments) {
            crewAssignments = crewAssignmentRepository.findByTripId(trip.getId()).stream()
                    .map(a -> new TripDtos.CrewAssignmentResponse(a.getId(), a.getCrew().getId(),
                            a.getCrew().getBadgeNo(), a.getCrew().getFullName(), a.getCrewType(),
                            a.getStatus(), a.getAssignedFrom(), a.getAssignedTo()))
                    .toList();
            busAssignments = busAssignmentRepository.findByTripId(trip.getId()).stream()
                    .map(a -> new TripDtos.BusAssignmentResponse(a.getId(), a.getBus().getId(),
                            a.getBus().getRegistrationNo(), a.getStatus(),
                            a.getAssignedFrom(), a.getAssignedTo()))
                    .toList();
        }
        return new TripDtos.TripResponse(
                trip.getId(),
                trip.getTripNumber(),
                trip.getTripDate(),
                trip.getScheduledDeparture(),
                trip.getScheduledArrival(),
                trip.getStatus().name(),
                trip.getDirection(),
                route.getId(),
                route.getCode(),
                route.getName(),
                bus.getId(),
                bus.getRegistrationNo(),
                bus.getDepot().getId(),
                bus.getDepot().getName(),
                route.getDivision().getId(),
                route.getDivision().getName(),
                crewAssignments,
                busAssignments,
                trip.getCreatedAt(),
                trip.getUpdatedAt());
    }

    private void audit(UserPrincipal principal, HttpServletRequest http, String action, Long id, Object detail) {
        auditService.record(principal == null ? null : principal.getUserId(), action, "TRIP", id, detail,
                http == null ? null : http.getRemoteAddr(),
                http == null ? null : http.getHeader("User-Agent"));
    }
}

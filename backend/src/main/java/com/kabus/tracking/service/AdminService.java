package com.kabus.tracking.service;

import com.kabus.tracking.domain.entity.Crew;
import com.kabus.tracking.domain.entity.LiveLocation;
import com.kabus.tracking.domain.entity.Trip;
import com.kabus.tracking.domain.enums.GpsSessionStatus;
import com.kabus.tracking.domain.enums.LiveStatus;
import com.kabus.tracking.domain.enums.TripStatus;
import com.kabus.tracking.domain.repository.BusRepository;
import com.kabus.tracking.domain.repository.CorporationRepository;
import com.kabus.tracking.domain.repository.CrewRepository;
import com.kabus.tracking.domain.repository.DepotRepository;
import com.kabus.tracking.domain.repository.DivisionRepository;
import com.kabus.tracking.domain.repository.GpsSessionRepository;
import com.kabus.tracking.domain.repository.LiveLocationRepository;
import com.kabus.tracking.domain.repository.RouteRepository;
import com.kabus.tracking.domain.repository.StaffRepository;
import com.kabus.tracking.domain.repository.TownRepository;
import com.kabus.tracking.domain.repository.TripRepository;
import com.kabus.tracking.security.UserPrincipal;
import com.kabus.tracking.support.LiveStatusPolicy;
import com.kabus.tracking.web.dto.AdminDtos;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Role + scope aware dashboard and list data for the admin app.
 *
 * <p>Every query is constrained to the caller's organizational scope, which
 * is always derived server-side. A user can never see data outside their
 * scope even if they craft requests directly.</p>
 */
@Service
public class AdminService {

    private final ScopeResolver scopeResolver;
    private final DivisionRepository divisionRepository;
    private final DepotRepository depotRepository;
    private final TownRepository townRepository;
    private final BusRepository busRepository;
    private final CrewRepository crewRepository;
    private final StaffRepository staffRepository;
    private final RouteRepository routeRepository;
    private final TripRepository tripRepository;
    private final LiveLocationRepository liveLocationRepository;
    private final GpsSessionRepository gpsSessionRepository;
    private final CorporationRepository corporationRepository;
    private final LiveStatusPolicy liveStatusPolicy;

    public AdminService(ScopeResolver scopeResolver,
                        DivisionRepository divisionRepository,
                        DepotRepository depotRepository,
                        TownRepository townRepository,
                        BusRepository busRepository,
                        CrewRepository crewRepository,
                        StaffRepository staffRepository,
                        RouteRepository routeRepository,
                        TripRepository tripRepository,
                        LiveLocationRepository liveLocationRepository,
                        GpsSessionRepository gpsSessionRepository,
                        CorporationRepository corporationRepository,
                        LiveStatusPolicy liveStatusPolicy) {
        this.scopeResolver = scopeResolver;
        this.divisionRepository = divisionRepository;
        this.depotRepository = depotRepository;
        this.townRepository = townRepository;
        this.busRepository = busRepository;
        this.crewRepository = crewRepository;
        this.staffRepository = staffRepository;
        this.routeRepository = routeRepository;
        this.tripRepository = tripRepository;
        this.liveLocationRepository = liveLocationRepository;
        this.gpsSessionRepository = gpsSessionRepository;
        this.corporationRepository = corporationRepository;
        this.liveStatusPolicy = liveStatusPolicy;
    }

    @Transactional(readOnly = true)
    public AdminDtos.DashboardResponse dashboard(UserPrincipal principal) {
        AccessScope scope = scopeResolver.resolve(principal);

        ScopeIds ids = scopeIds(scope);
        boolean townOnly = scope.townId() != null;
        List<Long> townIds = townOnly ? ids.townIds : List.of();
        List<Long> allDepots = scope.wholeSystem()
                ? depotRepository.findAll().stream().map(d -> d.getId()).toList()
                : ids.depotIds;
        List<Long> allDivisions = scope.wholeSystem()
                ? divisionRepository.findAll().stream().map(d -> d.getId()).toList()
                : ids.divisionIds;

        long divisions = scope.wholeSystem() ? divisionRepository.count()
                : divisionRepository.countByIdIn(allDivisions);
        long depots = scope.wholeSystem() ? depotRepository.count() : allDepots.size();
        long towns = scope.wholeSystem() ? townRepository.count() : ids.townIds.size();
        long buses = scope.wholeSystem() ? busRepository.count()
                : townOnly ? busRepository.countByTownIdIn(townIds) : busRepository.countByDepotIdIn(allDepots);
        long busesActive = townOnly
                ? busRepository.countInTownsByStatus(townIds, "ACTIVE")
                : busRepository.countInDepotsByStatus(allDepots, "ACTIVE");
        long crew = scope.wholeSystem() ? crewRepository.count()
                : townOnly ? crewRepository.countByStaffTownIdIn(townIds) : crewRepository.countByStaffDepotIdIn(allDepots);
        long staff = scope.wholeSystem() ? staffRepository.count()
                : townOnly ? staffRepository.countByTownIdIn(townIds) : staffRepository.countByDepotIdIn(allDepots);
        long routes = scope.wholeSystem() ? routeRepository.count()
                : routeRepository.countByDivisionIdIn(allDivisions);
        long activeTrips = townOnly
                ? tripRepository.countByStatusAndBusTownIdIn(TripStatus.RUNNING, townIds)
                : tripRepository.countByStatusAndBusDepotIdIn(TripStatus.RUNNING, allDepots);

        long busesLive = townOnly
                ? liveLocationRepository.countByBusTownIdInAndStatus(townIds, LiveStatus.LIVE)
                : liveLocationRepository.countByBusDepotIdInAndStatus(allDepots, LiveStatus.LIVE);
        long busesStale = townOnly
                ? liveLocationRepository.countByBusTownIdInAndStatus(townIds, LiveStatus.STALE)
                : liveLocationRepository.countByBusDepotIdInAndStatus(allDepots, LiveStatus.STALE);
        long busesOffline = townOnly
                ? liveLocationRepository.countByBusTownIdInAndStatus(townIds, LiveStatus.OFFLINE)
                : liveLocationRepository.countByBusDepotIdInAndStatus(allDepots, LiveStatus.OFFLINE);

        long activeSessions = townOnly
                ? gpsSessionRepository.countByStatusAndBusTownIdIn(GpsSessionStatus.ACTIVE, townIds)
                : gpsSessionRepository.countByStatusAndBusDepotIdIn(GpsSessionStatus.ACTIVE, allDepots);

        return new AdminDtos.DashboardResponse(
                principal.getRoles().stream().findFirst().map(Enum::name).orElse("NONE"),
                new AdminDtos.ScopeInfo(scope.wholeSystem(), scope.corporationId(), scope.divisionId(), scope.depotId(), scope.townId(),
                        scope.wholeSystem() ? List.of() : allDivisions,
                        scope.wholeSystem() ? List.of() : allDepots,
                        scope.wholeSystem() ? List.of() : ids.townIds),
                scope.wholeSystem() ? (int) corporationRepository.count() : 0,
                divisions, depots, towns, buses, busesActive,
                busesLive, busesStale, busesOffline, crew, staff, routes,
                activeTrips, activeSessions);
    }

    @Transactional(readOnly = true)
    public List<AdminDtos.LiveBusItem> liveBuses(UserPrincipal principal, int limit) {
        AccessScope scope = scopeResolver.resolve(principal);
        ScopeIds ids = scopeIds(scope);
        int max = Math.min(limit <= 0 ? 200 : limit, 1000);

        List<LiveLocation> rows = scope.wholeSystem()
                ? liveLocationRepository.findAllByOrderByCapturedAtDesc()
                : scope.townId() != null
                        ? liveLocationRepository.findByTownIds(ids.townIds)
                        : liveLocationRepository.findByDepotIds(ids.depotIds);
        return rows.stream().limit(max).map(l -> {
            var trip = l.getTrip();
            return new AdminDtos.LiveBusItem(
                    l.getBus().getId(), l.getBus().getRegistrationNo(), l.getBus().getBusType(),
                    trip == null ? null : trip.getId(),
                    l.getRoute() == null ? null : l.getRoute().getId(),
                    l.getRoute() == null ? null : l.getRoute().getName(),
                    l.getLatitude(), l.getLongitude(), l.getSpeedKmh(), l.getHeading(),
                    l.getStatus().name(), l.getCapturedAt());
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<AdminDtos.CrewItem> crew(UserPrincipal principal, int limit) {
        AccessScope scope = scopeResolver.resolve(principal);
        ScopeIds ids = scopeIds(scope);
        int max = Math.min(limit <= 0 ? 200 : limit, 1000);
        List<Crew> crewList = scope.wholeSystem()
                ? crewRepository.findAll()
                : scope.townId() != null
                        ? crewRepository.findByStaffTownIdIn(ids.townIds)
                        : crewRepository.findByStaffDepotIdIn(ids.depotIds);
        return crewList.stream().limit(max)
                .map(c -> new AdminDtos.CrewItem(c.getId(), c.getBadgeNo(), c.getFullName(),
                        c.getCrewType(), c.getStatus(), c.getDutyStatus()))
                .toList();
    }

    // ------------------------------------------------------------------

    private ScopeIds scopeIds(AccessScope scope) {
        if (scope.wholeSystem()) {
            return new ScopeIds(List.of(), List.of(), List.of(), true);
        }
        // A corporation-wide DIVISION_ADMIN covers every division in their corporation.
        if (scope.corporationId() != null) {
            List<Long> divisionIds = divisionRepository.findByCorporationId(scope.corporationId())
                    .stream().map(d -> d.getId()).toList();
            List<Long> depotIds = depotRepository.findByDivisionIdIn(divisionIds).stream()
                    .map(d -> d.getId()).toList();
            List<Long> townIds = townRepository.findByDepotIdIn(depotIds).stream().map(t -> t.getId()).toList();
            return new ScopeIds(divisionIds, depotIds, townIds, false);
        }
        // Most restrictive first: a town manager also carries a depot and a division.
        if (scope.townId() != null) {
            List<Long> depotIds = List.of(scope.depotId());
            return new ScopeIds(List.of(scope.divisionId()), depotIds, List.of(scope.townId()), false);
        }
        if (scope.depotId() != null) {
            List<Long> depotIds = List.of(scope.depotId());
            return new ScopeIds(List.of(scope.divisionId()), depotIds,
                    townRepository.findByDepotIdIn(depotIds).stream().map(t -> t.getId()).toList(), false);
        }
        List<Long> divisionIds = List.of(scope.divisionId());
        List<Long> depotIds = depotRepository.findByDivisionIdIn(divisionIds).stream().map(d -> d.getId()).toList();
        List<Long> townIds = townRepository.findByDepotIdIn(depotIds).stream().map(t -> t.getId()).toList();
        return new ScopeIds(divisionIds, depotIds, townIds, false);
    }

    private record ScopeIds(List<Long> divisionIds, List<Long> depotIds, List<Long> townIds, boolean whole) {
        List<Long> allDepotsOrNothing() {
            return whole ? List.of() : depotIds;
        }
    }
}
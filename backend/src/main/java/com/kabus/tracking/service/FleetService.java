package com.kabus.tracking.service;

import com.kabus.tracking.domain.entity.Bus;
import com.kabus.tracking.domain.entity.Depot;
import com.kabus.tracking.domain.entity.Town;
import com.kabus.tracking.domain.repository.BusRepository;
import com.kabus.tracking.domain.repository.DepotRepository;
import com.kabus.tracking.domain.repository.TownRepository;
import com.kabus.tracking.exception.ApiException;
import com.kabus.tracking.security.UserPrincipal;
import com.kabus.tracking.web.dto.FleetDtos;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fleet (bus) management. Reads and writes are restricted to the caller's
 * server-resolved {@link AccessScope}: a depot head only sees/touches buses in
 * their depot, a town manager only their town, a division admin only their
 * division. SUPER_ADMIN covers the whole system.
 */
@Service
public class FleetService {

    private final BusRepository busRepository;
    private final DepotRepository depotRepository;
    private final TownRepository townRepository;
    private final ScopeResolver scopeResolver;
    private final ScopeGuard scopeGuard;
    private final AuditService auditService;

    public FleetService(BusRepository busRepository,
                        DepotRepository depotRepository,
                        TownRepository townRepository,
                        ScopeResolver scopeResolver,
                        ScopeGuard scopeGuard,
                        AuditService auditService) {
        this.busRepository = busRepository;
        this.depotRepository = depotRepository;
        this.townRepository = townRepository;
        this.scopeResolver = scopeResolver;
        this.scopeGuard = scopeGuard;
        this.auditService = auditService;
    }

    // ------------------------------------------------------------------
    // Reads
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<FleetDtos.BusResponse> list(UserPrincipal principal, String search, int page, int size) {
        AccessScope scope = scopeResolver.resolve(principal);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        String term = (search == null || search.isBlank()) ? null : search.trim();

        Page<Bus> buses;
        if (scope.wholeSystem()) {
            buses = term == null
                    ? busRepository.findAll(pageable)
                    : busRepository.searchAll(term, pageable);
        } else if (scope.townId() != null) {
            List<Long> townIds = List.of(scope.townId());
            buses = term == null
                    ? busRepository.findByTownIdIn(townIds, pageable)
                    : busRepository.searchInTowns(townIds, term, pageable);
        } else {
            List<Long> depotIds = scopeResolver.depotIds(scope);
            if (depotIds.isEmpty()) {
                return Page.empty(pageable);
            }
            buses = term == null
                    ? busRepository.findByDepotIdIn(depotIds, pageable)
                    : busRepository.searchInDepots(depotIds, term, pageable);
        }
        return buses.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public FleetDtos.BusResponse get(UserPrincipal principal, Long id) {
        AccessScope scope = scopeResolver.resolve(principal);
        Bus bus = busRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Bus not found: " + id));
        requireVisible(scope, bus);
        return toResponse(bus);
    }

    // ------------------------------------------------------------------
    // Writes
    // ------------------------------------------------------------------

    @Transactional
    public FleetDtos.BusResponse create(UserPrincipal principal, FleetDtos.CreateBusRequest req,
                                        HttpServletRequest http) {
        AccessScope scope = scopeResolver.resolve(principal);
        Depot depot = depotRepository.findById(req.depotId())
                .orElseThrow(() -> ApiException.badRequest("Depot not found: " + req.depotId()));
        Town town = townRepository.findById(req.townId())
                .orElseThrow(() -> ApiException.badRequest("Town not found: " + req.townId()));
        if (!town.getDepot().getId().equals(depot.getId())) {
            throw ApiException.badRequest("Town " + town.getId() + " does not belong to depot " + depot.getId() + ".");
        }
        Long divisionId = depot.getDivision().getId();
        scopeGuard.requireWithin(scope, divisionId, depot.getId(), town.getId());

        if (busRepository.findByRegistrationNo(req.registrationNo().trim()).isPresent()) {
            throw ApiException.conflict("Registration number '" + req.registrationNo() + "' already exists.");
        }

        Bus bus = new Bus();
        bus.setDepot(depot);
        bus.setTown(town);
        applyEditable(bus, req.busType(), req.capacity(), req.fuelType(), req.makeModel(),
                req.manufactureYear(), req.gpsDeviceId(), req.gpsEnabled(), req.status());
        bus.setRegistrationNo(req.registrationNo().trim().toUpperCase());
        bus.setEnabled(true);
        busRepository.save(bus);

        audit(principal, http, "FLEET_BUS_CREATE", bus.getId(),
                Map.of("registrationNo", bus.getRegistrationNo(), "depotId", depot.getId(), "townId", town.getId()));
        return toResponse(bus);
    }

    @Transactional
    public FleetDtos.BusResponse update(UserPrincipal principal, Long id, FleetDtos.UpdateBusRequest req,
                                        HttpServletRequest http) {
        AccessScope scope = scopeResolver.resolve(principal);
        Bus bus = busRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Bus not found: " + id));
        requireVisible(scope, bus);

        if (req.townId() != null && !req.townId().equals(bus.getTown().getId())) {
            Town town = townRepository.findById(req.townId())
                    .orElseThrow(() -> ApiException.badRequest("Town not found: " + req.townId()));
            if (!town.getDepot().getId().equals(bus.getDepot().getId())) {
                throw ApiException.badRequest("Town " + town.getId() + " does not belong to this bus's depot.");
            }
            scopeGuard.requireWithin(scope, bus.getDepot().getDivision().getId(), bus.getDepot().getId(), town.getId());
            bus.setTown(town);
        }

        applyEditable(bus, req.busType(), req.capacity(), req.fuelType(), req.makeModel(),
                req.manufactureYear(), req.gpsDeviceId(), req.gpsEnabled(), req.status());
        busRepository.save(bus);

        audit(principal, http, "FLEET_BUS_UPDATE", bus.getId(),
                Map.of("registrationNo", bus.getRegistrationNo()));
        return toResponse(bus);
    }

    @Transactional
    public FleetDtos.BusResponse setEnabled(UserPrincipal principal, Long id, boolean enabled,
                                            HttpServletRequest http) {
        AccessScope scope = scopeResolver.resolve(principal);
        Bus bus = busRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Bus not found: " + id));
        requireVisible(scope, bus);
        bus.setEnabled(enabled);
        busRepository.save(bus);
        audit(principal, http, enabled ? "FLEET_BUS_ENABLE" : "FLEET_BUS_DISABLE", bus.getId(),
                Map.of("enabled", enabled));
        return toResponse(bus);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void requireVisible(AccessScope scope, Bus bus) {
        scopeGuard.requireWithin(scope, bus.getDepot().getDivision().getId(),
                bus.getDepot().getId(), bus.getTown().getId());
    }

    private void applyEditable(Bus bus, String busType, Integer capacity, String fuelType, String makeModel,
                               Integer manufactureYear, String gpsDeviceId, Boolean gpsEnabled, String status) {
        bus.setBusType(busType.trim().toUpperCase());
        bus.setCapacity(capacity);
        bus.setFuelType(fuelType == null || fuelType.isBlank() ? "DIESEL" : fuelType.trim().toUpperCase());
        bus.setMakeModel(blankToNull(makeModel));
        bus.setManufactureYear(manufactureYear);
        bus.setGpsDeviceId(blankToNull(gpsDeviceId));
        if (gpsEnabled != null) {
            bus.setGpsEnabled(gpsEnabled);
        }
        bus.setStatus(status == null || status.isBlank() ? "ACTIVE" : status.trim().toUpperCase());
    }

    private FleetDtos.BusResponse toResponse(Bus bus) {
        Depot depot = bus.getDepot();
        Town town = bus.getTown();
        return new FleetDtos.BusResponse(
                bus.getId(),
                bus.getRegistrationNo(),
                bus.getBusType(),
                bus.getCapacity(),
                bus.getFuelType(),
                bus.getMakeModel(),
                bus.getManufactureYear(),
                bus.getGpsDeviceId(),
                bus.isGpsEnabled(),
                bus.getStatus(),
                bus.isEnabled(),
                depot.getDivision().getId(),
                depot.getDivision().getName(),
                depot.getId(),
                depot.getName(),
                town.getId(),
                town.getName(),
                bus.getCreatedAt(),
                bus.getUpdatedAt());
    }

    private void audit(UserPrincipal principal, HttpServletRequest http, String action, Long id, Object detail) {
        auditService.record(principal == null ? null : principal.getUserId(), action, "BUS", id, detail,
                http == null ? null : http.getRemoteAddr(),
                http == null ? null : http.getHeader("User-Agent"));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

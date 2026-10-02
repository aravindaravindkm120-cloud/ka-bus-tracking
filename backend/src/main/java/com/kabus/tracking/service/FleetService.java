package com.kabus.tracking.service;

import com.kabus.tracking.domain.entity.Bus;
import com.kabus.tracking.domain.entity.BusNumber;
import com.kabus.tracking.domain.entity.Depot;
import com.kabus.tracking.domain.entity.Town;
import com.kabus.tracking.domain.repository.BusNumberRepository;
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
 * Fleet management, split into two levels:
 *
 * <ul>
 *   <li>the <em>bus number master</em> - which service numbers a depot runs,
 *       independent of any vehicle;</li>
 *   <li>the <em>fleet</em> - physical vehicles, each pointing at one bus number.</li>
 * </ul>
 *
 * Reads and writes are restricted to the caller's server-resolved
 * {@link AccessScope}: a depot head only sees/touches their own depot, a town
 * manager only their town, a division admin only their division. SUPER_ADMIN
 * covers the whole system.
 */
@Service
public class FleetService {

    private final BusRepository busRepository;
    private final BusNumberRepository busNumberRepository;
    private final DepotRepository depotRepository;
    private final TownRepository townRepository;
    private final ScopeResolver scopeResolver;
    private final ScopeGuard scopeGuard;
    private final AuditService auditService;

    public FleetService(BusRepository busRepository,
                        BusNumberRepository busNumberRepository,
                        DepotRepository depotRepository,
                        TownRepository townRepository,
                        ScopeResolver scopeResolver,
                        ScopeGuard scopeGuard,
                        AuditService auditService) {
        this.busRepository = busRepository;
        this.busNumberRepository = busNumberRepository;
        this.depotRepository = depotRepository;
        this.townRepository = townRepository;
        this.scopeResolver = scopeResolver;
        this.scopeGuard = scopeGuard;
        this.auditService = auditService;
    }

    // ------------------------------------------------------------------
    // Bus number master - reads
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<FleetDtos.BusNumberResponse> listBusNumbers(UserPrincipal principal, String search,
                                                           int page, int size) {
        AccessScope scope = scopeResolver.resolve(principal);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        String term = (search == null || search.isBlank()) ? null : search.trim();

        Page<BusNumber> rows;
        if (scope.wholeSystem()) {
            rows = term == null
                    ? busNumberRepository.findAllByOrderByBusNumberAsc(pageable)
                    : busNumberRepository.searchAll(term, pageable);
        } else if (scope.townId() != null) {
            // A town manager sees only enabled bus numbers serving their town.
            List<Long> townIds = List.of(scope.townId());
            rows = term == null
                    ? busNumberRepository.findByTownIdInAndEnabledTrueOrderByBusNumberAsc(townIds, pageable)
                    : busNumberRepository.searchInTowns(townIds, term, pageable);
        } else {
            List<Long> depotIds = scopeResolver.depotIds(scope);
            if (depotIds.isEmpty()) {
                return Page.empty(pageable);
            }
            rows = term == null
                    ? busNumberRepository.findByDepotIdInOrderByBusNumberAsc(depotIds, pageable)
                    : busNumberRepository.searchInDepots(depotIds, term, pageable);
        }
        return rows.map(this::toBusNumberResponse);
    }

    @Transactional(readOnly = true)
    public FleetDtos.BusNumberResponse getBusNumber(UserPrincipal principal, Long id) {
        AccessScope scope = scopeResolver.resolve(principal);
        BusNumber row = busNumberRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Bus number not found: " + id));
        requireVisible(scope, row);
        return toBusNumberResponse(row);
    }

    /**
     * Enabled bus numbers for one depot, for the fleet and trip assignment
     * dropdowns. The depot must itself be in scope.
     */
    @Transactional(readOnly = true)
    public List<FleetDtos.BusNumberResponse> listForDepot(UserPrincipal principal, Long depotId) {
        AccessScope scope = scopeResolver.resolve(principal);
        Depot depot = depotRepository.findById(depotId)
                .orElseThrow(() -> ApiException.notFound("Depot not found: " + depotId));
        scopeGuard.requireWithin(scope, depot.getDivision().getId(), depot.getId(), null);
        return busNumberRepository.findByDepotIdInAndEnabledTrueOrderByBusNumberAsc(List.of(depot.getId()))
                .stream()
                .map(this::toBusNumberResponse)
                .toList();
    }

    // ------------------------------------------------------------------
    // Bus number master - writes
    // ------------------------------------------------------------------

    @Transactional
    public FleetDtos.BusNumberResponse createBusNumber(UserPrincipal principal,
                                                      FleetDtos.CreateBusNumberRequest req,
                                                      HttpServletRequest http) {
        AccessScope scope = scopeResolver.resolve(principal);
        Depot depot = depotRepository.findById(req.depotId())
                .orElseThrow(() -> ApiException.badRequest("Depot not found: " + req.depotId()));
        Town town = townRepository.findById(req.townId())
                .orElseThrow(() -> ApiException.badRequest("Town not found: " + req.townId()));
        if (!town.getDepot().getId().equals(depot.getId())) {
            throw ApiException.badRequest("Town " + town.getId() + " does not belong to depot " + depot.getId() + ".");
        }
        scopeGuard.requireWithin(scope, depot.getDivision().getId(), depot.getId(), town.getId());

        String busNumber = req.busNumber().trim().toUpperCase();
        String busType = normaliseBusType(req.busType());
        if (busNumberRepository.existsByDepotIdAndBusNumber(depot.getId(), busNumber)) {
            throw ApiException.conflict("Bus number '" + busNumber + "' already exists for this depot.");
        }

        BusNumber row = new BusNumber();
        row.setDepot(depot);
        row.setTown(town);
        row.setDivision(depot.getDivision());
        row.setCorporation(depot.getDivision().getCorporation());
        row.setBusNumber(busNumber);
        row.setBusType(busType);
        row.setEnabled(true);
        busNumberRepository.save(row);

        audit(principal, http, "FLEET_BUS_NUMBER_CREATE", row.getId(),
                Map.of("busNumber", busNumber, "depotId", depot.getId(), "townId", town.getId()));
        return toBusNumberResponse(row);
    }

    @Transactional
    public FleetDtos.BusNumberResponse updateBusNumber(UserPrincipal principal, Long id,
                                                      FleetDtos.UpdateBusNumberRequest req,
                                                      HttpServletRequest http) {
        AccessScope scope = scopeResolver.resolve(principal);
        BusNumber row = busNumberRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Bus number not found: " + id));
        requireVisible(scope, row);

        if (req.townId() != null && !req.townId().equals(row.getTown().getId())) {
            Town town = townRepository.findById(req.townId())
                    .orElseThrow(() -> ApiException.badRequest("Town not found: " + req.townId()));
            if (!town.getDepot().getId().equals(row.getDepot().getId())) {
                throw ApiException.badRequest("Town " + town.getId() + " does not belong to this bus number's depot.");
            }
            scopeGuard.requireWithin(scope, row.getDivision().getId(), row.getDepot().getId(), town.getId());
            row.setTown(town);
        }

        row.setBusType(normaliseBusType(req.busType()));
        busNumberRepository.save(row);

        audit(principal, http, "FLEET_BUS_NUMBER_UPDATE", row.getId(),
                Map.of("busNumber", row.getBusNumber(), "busType", row.getBusType()));
        return toBusNumberResponse(row);
    }

    @Transactional
    public FleetDtos.BusNumberResponse setBusNumberEnabled(UserPrincipal principal, Long id, boolean enabled,
                                                          HttpServletRequest http) {
        AccessScope scope = scopeResolver.resolve(principal);
        BusNumber row = busNumberRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Bus number not found: " + id));
        requireVisible(scope, row);

        if (!enabled) {
            long vehicles = busRepository.findByDepotIdIn(List.of(row.getDepot().getId())).stream()
                    .filter(b -> row.getId().equals(b.getBusNumber().getId()))
                    .count();
            if (vehicles > 0) {
                throw ApiException.conflict("Bus number '" + row.getBusNumber() + "' still has "
                        + vehicles + " vehicle(s) assigned and cannot be disabled.");
            }
        }

        row.setEnabled(enabled);
        busNumberRepository.save(row);
        audit(principal, http, enabled ? "FLEET_BUS_NUMBER_ENABLE" : "FLEET_BUS_NUMBER_DISABLE", row.getId(),
                Map.of("enabled", enabled));
        return toBusNumberResponse(row);
    }

    @Transactional
    public void deleteBusNumber(UserPrincipal principal, Long id, HttpServletRequest http) {
        AccessScope scope = scopeResolver.resolve(principal);
        BusNumber row = busNumberRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Bus number not found: " + id));
        requireVisible(scope, row);

        boolean inUse = busRepository.findByDepotIdIn(List.of(row.getDepot().getId())).stream()
                .anyMatch(b -> row.getId().equals(b.getBusNumber().getId()));
        if (inUse) {
            throw ApiException.conflict("Bus number '" + row.getBusNumber()
                    + "' is assigned to a vehicle and cannot be deleted.");
        }

        audit(principal, http, "FLEET_BUS_NUMBER_DELETE", row.getId(),
                Map.of("busNumber", row.getBusNumber()));
        busNumberRepository.delete(row);
    }

    // ------------------------------------------------------------------
    // Fleet - reads
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
    // Fleet - writes
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

        BusNumber busNumber = requireBusNumber(req.busNumberId(), depot, town);

        Bus bus = new Bus();
        bus.setDepot(depot);
        bus.setTown(town);
        bus.setBusNumber(busNumber);
        applyEditable(bus, req.capacity(), req.fuelType(), req.makeModel(),
                req.manufactureYear(), req.gpsDeviceId(), req.gpsEnabled(), req.status());
        bus.setEnabled(true);
        busRepository.save(bus);

        audit(principal, http, "FLEET_BUS_CREATE", bus.getId(),
                Map.of("busNumberId", busNumber.getId(), "depotId", depot.getId(), "townId", town.getId()));
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

        applyEditable(bus, req.capacity(), req.fuelType(), req.makeModel(),
                req.manufactureYear(), req.gpsDeviceId(), req.gpsEnabled(), req.status());
        busRepository.save(bus);

        audit(principal, http, "FLEET_BUS_UPDATE", bus.getId(),
                Map.of("busNumberId", bus.getBusNumber().getId()));
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

    /**
     * Resolves the chosen bus number and checks it is enabled, belongs to the
     * target depot, and offers a town the vehicle may legally be registered in.
     */
    private BusNumber requireBusNumber(Long busNumberId, Depot depot, Town town) {
        BusNumber busNumber = busNumberRepository.findById(busNumberId)
                .orElseThrow(() -> ApiException.badRequest("Bus number not found: " + busNumberId));
        if (!busNumber.isEnabled()) {
            throw ApiException.badRequest("Bus number '" + busNumber.getBusNumber() + "' is disabled.");
        }
        if (!busNumber.getDepot().getId().equals(depot.getId())) {
            throw ApiException.badRequest("Bus number '" + busNumber.getBusNumber()
                    + "' does not belong to depot " + depot.getId() + ".");
        }
        if (!busNumber.getTown().getId().equals(town.getId())) {
            throw ApiException.badRequest("Bus number '" + busNumber.getBusNumber()
                    + "' serves town " + busNumber.getTown().getId() + "; the vehicle must be registered there.");
        }
        return busNumber;
    }

    private void requireVisible(AccessScope scope, Bus bus) {
        scopeGuard.requireWithin(scope, bus.getDepot().getDivision().getId(),
                bus.getDepot().getId(), bus.getTown().getId());
    }

    private void requireVisible(AccessScope scope, BusNumber row) {
        scopeGuard.requireWithin(scope, row.getDivision().getId(),
                row.getDepot().getId(), row.getTown().getId());
    }

    /**
     * Bus types are a closed set taken from the values the schema already
     * documents, so the dropdown and the database cannot drift apart.
     */
    private static String normaliseBusType(String busType) {
        String candidate = busType.trim().toUpperCase();
        if (!FleetDtos.BUS_TYPES.contains(candidate)) {
            throw ApiException.badRequest("Bus type must be one of: " + String.join(", ", FleetDtos.BUS_TYPES) + ".");
        }
        return candidate;
    }

    private void applyEditable(Bus bus, Integer capacity, String fuelType, String makeModel,
                               Integer manufactureYear, String gpsDeviceId, Boolean gpsEnabled, String status) {
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

    private FleetDtos.BusNumberResponse toBusNumberResponse(BusNumber row) {
        Depot depot = row.getDepot();
        Town town = row.getTown();
        long vehicleCount = busRepository.findByDepotIdIn(List.of(depot.getId())).stream()
                .filter(b -> row.getId().equals(b.getBusNumber().getId()))
                .count();
        return new FleetDtos.BusNumberResponse(
                row.getId(),
                row.getBusNumber(),
                row.getBusType(),
                row.isEnabled(),
                row.getCorporation().getId(),
                row.getDivision().getId(),
                depot.getId(),
                depot.getName(),
                town.getId(),
                town.getName(),
                vehicleCount,
                row.getCreatedAt(),
                row.getUpdatedAt());
    }

    private FleetDtos.BusResponse toResponse(Bus bus) {
        Depot depot = bus.getDepot();
        Town town = bus.getTown();
        BusNumber busNumber = bus.getBusNumber();
        return new FleetDtos.BusResponse(
                bus.getId(),
                busNumber.getBusNumber(),
                busNumber.getBusType(),
                busNumber.getId(),
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
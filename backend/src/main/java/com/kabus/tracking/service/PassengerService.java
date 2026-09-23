package com.kabus.tracking.service;

import com.kabus.tracking.domain.entity.Bus;
import com.kabus.tracking.domain.entity.Favorite;
import com.kabus.tracking.domain.entity.LiveLocation;
import com.kabus.tracking.domain.entity.RecentSearch;
import com.kabus.tracking.domain.entity.Route;
import com.kabus.tracking.domain.entity.RouteStop;
import com.kabus.tracking.domain.entity.Trip;
import com.kabus.tracking.domain.enums.LiveStatus;
import com.kabus.tracking.domain.enums.TripStatus;
import com.kabus.tracking.domain.repository.BusRepository;
import com.kabus.tracking.domain.repository.FavoriteRepository;
import com.kabus.tracking.domain.repository.LiveLocationRepository;
import com.kabus.tracking.domain.repository.RecentSearchRepository;
import com.kabus.tracking.domain.repository.RouteRepository;
import com.kabus.tracking.domain.repository.RouteStopRepository;
import com.kabus.tracking.domain.repository.TripRepository;
import com.kabus.tracking.exception.ApiException;
import com.kabus.tracking.support.Geo;
import com.kabus.tracking.support.LiveStatusPolicy;
import com.kabus.tracking.support.SettingsService;
import com.kabus.tracking.web.dto.PassengerDtos;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Public read-only passenger API. No authentication required. All queries are
 * paginated and cheap - the heavy location_history table is never scanned.
 */
@Service
public class PassengerService {

    private final RouteRepository routeRepository;
    private final RouteStopRepository routeStopRepository;
    private final TripRepository tripRepository;
    private final BusRepository busRepository;
    private final LiveLocationRepository liveLocationRepository;
    private final FavoriteRepository favoriteRepository;
    private final RecentSearchRepository recentSearchRepository;
    private final LiveStatusPolicy liveStatusPolicy;
    private final SettingsService settings;

    public PassengerService(RouteRepository routeRepository,
                            RouteStopRepository routeStopRepository,
                            TripRepository tripRepository,
                            BusRepository busRepository,
                            LiveLocationRepository liveLocationRepository,
                            FavoriteRepository favoriteRepository,
                            RecentSearchRepository recentSearchRepository,
                            LiveStatusPolicy liveStatusPolicy,
                            SettingsService settings) {
        this.routeRepository = routeRepository;
        this.routeStopRepository = routeStopRepository;
        this.tripRepository = tripRepository;
        this.busRepository = busRepository;
        this.liveLocationRepository = liveLocationRepository;
        this.favoriteRepository = favoriteRepository;
        this.recentSearchRepository = recentSearchRepository;
        this.liveStatusPolicy = liveStatusPolicy;
        this.settings = settings;
    }

    @Transactional(readOnly = true)
    public PassengerDtos.PublicConfig publicConfig() {
        return new PassengerDtos.PublicConfig(
                liveStatusPolicy.liveSeconds(),
                liveStatusPolicy.staleSeconds(),
                liveStatusPolicy.liveSeconds(),
                liveStatusPolicy.staleSeconds(),
                200,
                true);
    }

    @Transactional(readOnly = true)
    public List<PassengerDtos.RouteDto> routeSuggestions(String term, int limit) {
        if (term == null || term.isBlank()) {
            return List.of();
        }
        var routes = routeRepository.searchByTerm(term, PageRequest.of(0, Math.min(limit, 20)));
        return routes.stream().map(r -> toRouteDto(r, List.of(), "OUTBOUND")).toList();
    }

    /**
     * Stop autocomplete for the passenger FROM/TO fields. Serves the official
     * database names from {@code route_stops} plus route terminals of enabled
     * ACTIVE routes — the same authoritative data the route/search endpoints
     * use. Matching is normalized (trim, NFKC, whitespace-collapsed,
     * case-insensitive) and limited server-side so the endpoint stays cheap at
     * scale.
     */
    @Transactional(readOnly = true)
    public List<PassengerDtos.StopSuggestion> stopSuggestions(String term, int limit) {
        String normalized = norm(term);
        if (normalized.isEmpty()) {
            return List.of();
        }
        int cap = Math.min(limit <= 0 ? 8 : limit, 20);

        List<String> names = new ArrayList<>();
        names.addAll(routeRepository.searchStopNames(normalized, PageRequest.of(0, cap * 3)));
        names.addAll(routeRepository.searchOriginNames(normalized, PageRequest.of(0, cap * 2)));
        names.addAll(routeRepository.searchDestinationNames(normalized, PageRequest.of(0, cap * 2)));
        if (names.isEmpty()) {
            return List.of();
        }

        // De-duplicate by normalized key, keeping the first official spelling.
        List<String> unique = new ArrayList<>();
        Map<String, String> officialByKey = new HashMap<>();
        Set<String> keys = new HashSet<>();
        for (String name : names) {
            if (name == null || name.isBlank()) {
                continue;
            }
            String key = norm(name);
            if (!keys.add(key)) {
                continue;
            }
            unique.add(name);
            officialByKey.put(key, name);
        }
        if (unique.isEmpty()) {
            return List.of();
        }

        // Coordinates for the matched names (bulk query, best-effort).
        Map<String, BigDecimal[]> coordsByKey = new HashMap<>();
        List<String> lowerNames = unique.stream().map(PassengerService::norm).toList();
        for (RouteStop rs : routeStopRepository.findCoordinatesForNames(lowerNames)) {
            String key = norm(rs.getStopName());
            if (!coordsByKey.containsKey(key) && rs.getLatitude() != null && rs.getLongitude() != null) {
                coordsByKey.put(key, new BigDecimal[]{rs.getLatitude(), rs.getLongitude()});
            }
        }

        unique.sort(Comparator
                .comparingInt((String name) -> rankFor(name, normalized))
                .thenComparing(name -> officialByKey.get(norm(name))));

        List<PassengerDtos.StopSuggestion> out = new ArrayList<>(cap);
        for (String name : unique) {
            if (out.size() >= cap) {
                break;
            }
            String key = norm(name);
            BigDecimal[] c = coordsByKey.get(key);
            out.add(new PassengerDtos.StopSuggestion(
                    officialByKey.get(key), c == null ? null : c[0], c == null ? null : c[1]));
        }
        return out;
    }

    /** 0 = exact, 1 = prefix, 2 = substring, so exact matches rank first. */
    private static int rankFor(String name, String normalizedTerm) {
        String n = norm(name);
        if (n.equals(normalizedTerm)) {
            return 0;
        }
        if (n.startsWith(normalizedTerm)) {
            return 1;
        }
        return 2;
    }

    /** Full route detail with ordered stops, for the passenger map. */
    @Transactional(readOnly = true)
    public PassengerDtos.RouteDto routeDetails(Long routeId, String direction) {
        Route route = routeRepository.findById(routeId)
                .orElseThrow(() -> ApiException.notFound("Route not found."));
        List<RouteStop> routeStops = routeStopRepository.findByRouteIdOrderByStopOrder(routeId);
        return toRouteDto(route, stopDtos(oriented(routeStops, direction)), direction);
    }

    @Transactional(readOnly = true)
    public PassengerDtos.SearchResult search(String from, String to, int page, int size) {
        if (from == null || from.isBlank() || to == null || to.isBlank()) {
            throw ApiException.badRequest("Both 'from' and 'to' are required for a search.");
        }
        String fromNorm = norm(from);
        String toNorm = norm(to);
        if (fromNorm.isEmpty() || toNorm.isEmpty() || fromNorm.equals(toNorm)) {
            return new PassengerDtos.SearchResult(from, to, null, 0, List.of(), List.of());
        }
        size = Math.min(size, 50);
        Pageable pageable = PageRequest.of(Math.max(page, 0), size);

        var candidates = routeRepository.searchRouteBetweenStops(fromNorm, toNorm, pageable);
        if (candidates.isEmpty()) {
            return new PassengerDtos.SearchResult(from, to, null, 0, List.of(), List.of());
        }

        // Keep only routes with a real stop relationship in the requested
        // corridor, and resolve the direction of travel for each.
        List<Route> corridorRoutes = new ArrayList<>();
        String corridorDirection = null;
        for (Route r : candidates) {
            String d = resolveDirection(r, from, to);
            if (d == null) {
                continue;
            }
            corridorRoutes.add(r);
            if (corridorDirection == null) {
                corridorDirection = d;
            }
        }
        if (corridorRoutes.isEmpty()) {
            // No direct corridor service: the answer can only be connecting
            // journeys built from the real route_stops graph.
            List<PassengerDtos.ConnectingJourney> connecting = findConnecting(from, to, fromNorm, toNorm);
            if (connecting.isEmpty()) {
                return new PassengerDtos.SearchResult(from, to, null, 0, List.of(), List.of());
            }
            // Surface the canonical official names of the best connection.
            PassengerDtos.ConnectingJourney first = connecting.get(0);
            return new PassengerDtos.SearchResult(
                    first.from(), first.to(), null, 0, List.of(), List.of(), connecting);
        }

        // Canonical official names so free-text / lowercase input is replaced
        // by the authoritative database spelling ("kadwad" -> "Kadwad").
        Route canonicalRoute = corridorRoutes.get(0);
        List<RouteStop> canonicalStops =
                routeStopRepository.findByRouteIdOrderByStopOrder(canonicalRoute.getId());
        String canonicalFrom = bestStopName(canonicalStops, canonicalRoute, from);
        String canonicalTo = bestStopName(canonicalStops, canonicalRoute, to);

        // Connecting journeys (1 or 2 transfers) built purely from the real
        // route_stops graph. Computed whether or not direct service exists so
        // the API can offer both; the frontend shows direct results first.
        List<PassengerDtos.ConnectingJourney> connecting = findConnecting(from, to, fromNorm, toNorm);

        List<PassengerDtos.RouteDto> corridors = new ArrayList<>(corridorRoutes.size());
        for (Route r : corridorRoutes) {
            List<RouteStop> stops = routeStopRepository.findByRouteIdOrderByStopOrder(r.getId());
            corridors.add(corridorRouteDto(r, stops, corridorDirection, canonicalFrom, canonicalTo, from, to));
        }

        List<Long> routeIds = corridorRoutes.stream().map(Route::getId).toList();
        var trips = tripRepository.findByRouteIdInAndStatus(routeIds, TripStatus.RUNNING);
        Set<Long> tripBusIds = trips.stream().map(t -> t.getBus().getId()).collect(Collectors.toSet());

        var liveByBus = liveLocationRepository.findByBusIdIn(tripBusIds.isEmpty()
                        ? List.of(-1L) : new ArrayList<>(tripBusIds))
                .stream().collect(Collectors.toMap(l -> l.getBus().getId(), l -> l, (a, b) -> a));

        // Resolve the direction of travel for each candidate route relative to
        // the given from/to pair. Stops are stored in OUTBOUND (natural) order,
        // so forward = from *before* to, reverse = from *after* to.
        List<PassengerDtos.BusSummary> summaries = new ArrayList<>();
        for (Trip trip : trips) {
            Route route = trip.getRoute();
            String required = resolveDirection(route, from, to);
            if (required == null) {
                continue;
            }
            // Never fabricate a reverse-direction bus: a trip is only returned
            // when its own direction matches the direction implied by the search.
            String tripDirection = trip.getDirection() == null ? "OUTBOUND" : trip.getDirection();
            if (!required.equalsIgnoreCase(tripDirection)) {
                continue;
            }
            LiveLocation live = liveByBus.get(trip.getBus().getId());
            summaries.add(toSummary(trip.getBus(), route, tripDirection, live));
        }
        // Deterministic order: live first, then registration.
        summaries.sort(Comparator
                .comparingInt((PassengerDtos.BusSummary s) -> s.status() == LiveStatus.OFFLINE ? 1 : 0)
                .thenComparing(s -> s.registrationNo()));
        return new PassengerDtos.SearchResult(
                canonicalFrom, canonicalTo, corridorDirection, summaries.size(), summaries, corridors, connecting);
    }

    @Transactional(readOnly = true)
    public PassengerDtos.NearbyResult nearby(BigDecimal lat, BigDecimal lon, BigDecimal radiusKm, int limit) {
        if (lat == null || lon == null) {
            throw ApiException.badRequest("latitude and longitude are required.");
        }
        if (lat.doubleValue() < -90.0 || lat.doubleValue() > 90.0
                || lon.doubleValue() < -180.0 || lon.doubleValue() > 180.0) {
            throw ApiException.badRequest("Invalid coordinates.");
        }
        double radius = radiusKm == null ? 5.0 : Math.min(Math.max(radiusKm.doubleValue(), 0.1), 50.0);
        double latDelta = Geo.latDeltaKm(radius);
        double lonDelta = Geo.lonDeltaKm(radius, lat.doubleValue());
        int max = Math.min(limit <= 0 ? 50 : limit, 100);

        List<LiveLocation> candidates = liveLocationRepository.findWithinBounds(
                lat.subtract(BigDecimal.valueOf(latDelta)),
                lat.add(BigDecimal.valueOf(latDelta)),
                lon.subtract(BigDecimal.valueOf(lonDelta)),
                lon.add(BigDecimal.valueOf(lonDelta)),
                LiveStatus.OFFLINE);

        List<PassengerDtos.BusSummary> results = candidates.stream()
                .filter(l -> Geo.distanceKm(lat, lon, l.getLatitude(), l.getLongitude()) <= radius)
                .limit(max)
                .map(l -> toSummary(l.getBus(), l.getTrip() == null ? null : l.getTrip().getRoute(),
                        l.getTrip() == null ? "OUTBOUND" : (l.getTrip().getDirection() == null ? "OUTBOUND" : l.getTrip().getDirection()), l))
                .toList();

        return new PassengerDtos.NearbyResult(results);
    }

    @Transactional(readOnly = true)
    public PassengerDtos.BusDetails busDetails(Long busId) {
        Bus bus = busRepository.findById(busId)
                .orElseThrow(() -> ApiException.notFound("Bus not found."));
        LiveLocation live = liveLocationRepository.findByBusId(busId).orElse(null);
        Trip running = tripRepository.findFirstByBusIdAndStatus(busId, TripStatus.RUNNING).orElse(null);

        Route route = null;
        List<PassengerDtos.StopDto> stops = new ArrayList<>();
        PassengerDtos.StopDto nextStop = null;
        BigDecimal nextDistance = null;

        if (running != null && running.getRoute() != null) {
            route = running.getRoute();
            String direction = running.getDirection() == null ? "OUTBOUND" : running.getDirection();
            List<RouteStop> routeStops = oriented(
                    routeStopRepository.findByRouteIdOrderByStopOrder(route.getId()), direction);
            stops = stopDtos(routeStops);
            if (live != null) {
                int idx = nextStopIndex(routeStops, live.getLatitude(), live.getLongitude());
                if (idx >= 0) {
                    RouteStop ns = routeStops.get(idx);
                    nextStop = new PassengerDtos.StopDto(ns.getStopOrder(), ns.getStopName(),
                            ns.getLatitude(), ns.getLongitude(), ns.getDistanceFromStart());
                    nextDistance = BigDecimal.valueOf(
                            Geo.distanceKm(live.getLatitude(), live.getLongitude(),
                                    ns.getLatitude(), ns.getLongitude()));
                }
            }
        }

        return new PassengerDtos.BusDetails(
                bus.getId(), bus.getRegistrationNo(), bus.getBusType(),
                bus.getStatus(),
                running == null ? null : running.getId(),
                running == null ? null : running.getTripNumber(),
                route == null ? null : toRouteDto(route, stops,
                        running == null ? "OUTBOUND" : (running.getDirection() == null ? "OUTBOUND" : running.getDirection())),
                nextStop, nextDistance,
                live == null ? null : live.getLatitude(),
                live == null ? null : live.getLongitude(),
                live == null ? null : live.getSpeedKmh(),
                live == null ? null : live.getHeading(),
                live == null ? null : live.getAccuracyM(),
                live == null ? null : live.getAltitudeM(),
                live == null ? null : qualifiedLiveStatus(live),
                live == null ? null : live.getCapturedAt());
    }

    @Transactional(readOnly = true)
    public Page<PassengerDtos.BusSummary> liveBusPage(int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50));
        Page<Bus> buses = busRepository.findAll(pageable);
        List<Long> busIds = buses.getContent().stream().map(Bus::getId).toList();
        var liveByBus = liveLocationRepository.findByBusIdIn(busIds).stream()
                .collect(Collectors.toMap(l -> l.getBus().getId(), l -> l, (a, b) -> a));
        var runningTrips = tripRepository.findByBusIdInAndStatus(busIds, TripStatus.RUNNING).stream()
                .collect(Collectors.toMap(t -> t.getBus().getId(), t -> t, (a, b) -> a));
        return buses.map(b -> toSummary(b,
                runningTrips.get(b.getId()) == null ? null : runningTrips.get(b.getId()).getRoute(),
                runningTrips.get(b.getId()) == null ? "OUTBOUND"
                        : (runningTrips.get(b.getId()).getDirection() == null
                                ? "OUTBOUND" : runningTrips.get(b.getId()).getDirection()),
                liveByBus.get(b.getId())));
    }

    @Transactional(readOnly = true)
    public Page<Bus> busesPage(int page, int size) {
        return busRepository.findAll(PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
    }

    @Transactional
    public void recordSearch(String deviceId, String from, String to) {
        if (deviceId == null || deviceId.isBlank() || from == null || to == null) {
            return;
        }
        recentSearchRepository.deleteByDeviceIdAndFromNameAndToName(deviceId, from, to);
        RecentSearch search = new RecentSearch();
        search.setDeviceId(deviceId.substring(0, Math.min(deviceId.length(), 80)));
        search.setFromName(from);
        search.setToName(to);
        recentSearchRepository.save(search);
    }

    @Transactional(readOnly = true)
    public List<PassengerDtos.RecentSearchItem> recentSearches(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return List.of();
        }
        return recentSearchRepository.findTop10ByDeviceIdOrderByCreatedAtDesc(deviceId).stream()
                .map(s -> new PassengerDtos.RecentSearchItem(s.getFromName(), s.getToName(), s.getCreatedAt()))
                .toList();
    }

    @Transactional
    public Favorite addFavorite(String deviceId, String itemType, Long itemId, String label,
                                BigDecimal lat, BigDecimal lon) {
        if (deviceId == null || deviceId.isBlank()) {
            throw ApiException.badRequest("deviceId is required.");
        }
        boolean exists = favoriteRepository.findFirstByDeviceIdAndItemTypeAndItemId(deviceId, itemType, itemId).isPresent();
        if (exists) {
            throw ApiException.conflict("Already a favorite.");
        }
        Favorite favorite = new Favorite();
        favorite.setDeviceId(deviceId.substring(0, Math.min(deviceId.length(), 80)));
        favorite.setItemType(itemType);
        favorite.setItemId(itemId);
        favorite.setLabel(label);
        favorite.setLatitude(lat);
        favorite.setLongitude(lon);
        return favoriteRepository.save(favorite);
    }

    @Transactional
    public void removeFavorite(String deviceId, String itemType, Long itemId) {
        favoriteRepository.deleteByDeviceIdAndItemTypeAndItemId(deviceId, itemType, itemId);
    }

    @Transactional(readOnly = true)
    public List<PassengerDtos.FavoriteItem> favorites(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return List.of();
        }
        return favoriteRepository.findByDeviceIdOrderByCreatedAtDesc(deviceId).stream()
                .map(f -> new PassengerDtos.FavoriteItem(
                        f.getId(), f.getItemType(), f.getItemId(), f.getLabel(), f.getLatitude(), f.getLongitude()))
                .toList();
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private PassengerDtos.BusSummary toSummary(Bus bus, Route route, String direction, LiveLocation live) {
        LiveStatus status = live == null ? LiveStatus.OFFLINE : qualifiedLiveStatus(live);
        LocalDateTime lastUpdate = live == null ? null : live.getCapturedAt();
        String dir = direction == null || direction.isBlank() ? "OUTBOUND" : direction;
        // Oriented origin/destination: when a bus travels INBOUND (reverse of the
        // stored route order) the public terminals swap sides.
        String origin = route == null ? null
                : ("INBOUND".equalsIgnoreCase(dir) ? route.getDestination() : route.getOrigin());
        String destination = route == null ? null
                : ("INBOUND".equalsIgnoreCase(dir) ? route.getOrigin() : route.getDestination());
        return new PassengerDtos.BusSummary(
                bus.getId(), bus.getRegistrationNo(), bus.getBusType(),
                route == null ? null : route.getId(),
                route == null ? null : route.getName(),
                route == null ? null : route.getCode(),
                dir,
                origin,
                destination,
                live == null ? null : live.getLatitude(),
                live == null ? null : live.getLongitude(),
                live == null ? null : live.getSpeedKmh(),
                live == null ? null : live.getHeading(),
                live == null ? null : live.getAccuracyM(),
                status,
                lastUpdate);
    }

    private LiveStatus qualifiedLiveStatus(LiveLocation live) {
        if (live.getStatus() == LiveStatus.OFFLINE) {
            return LiveStatus.OFFLINE;
        }
        return liveStatusPolicy.statusNow(live.getCapturedAt());
    }

    private int nextStopIndex(List<RouteStop> stops, BigDecimal lat, BigDecimal lon) {
        if (stops == null || stops.isEmpty() || lat == null || lon == null) {
            return -1;
        }
        // nearest-by-distance, then the stop after it; default to the nearest.
        int nearest = 0;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < stops.size(); i++) {
            double d = Geo.distanceKm(lat, lon, stops.get(i).getLatitude(), stops.get(i).getLongitude());
            if (d < best) {
                best = d;
                nearest = i;
            }
        }
        int next = nearest + 1 < stops.size() ? nearest + 1 : nearest;
        return next;
    }

    private PassengerDtos.RouteDto toRouteDto(Route route, List<PassengerDtos.StopDto> stops, String direction) {
        String dir = direction == null || direction.isBlank() ? "OUTBOUND" : direction;
        // Oriented terminals: in the INBOUND direction origin/destination swap.
        String origin = originFor(route, dir);
        String destination = destinationFor(route, dir);
        return new PassengerDtos.RouteDto(
                route.getId(), route.getCode(), route.getName(),
                origin, destination, dir, stops);
    }

    private static String originFor(Route route, String direction) {
        return "INBOUND".equalsIgnoreCase(direction) ? route.getDestination() : route.getOrigin();
    }

    private static String destinationFor(Route route, String direction) {
        return "INBOUND".equalsIgnoreCase(direction) ? route.getOrigin() : route.getDestination();
    }

    /** Reverse a stop list when the given direction is INBOUND; otherwise keep natural order. */
    private List<RouteStop> oriented(List<RouteStop> stops, String direction) {
        if (stops == null || stops.isEmpty()
                || !"INBOUND".equalsIgnoreCase(direction)) {
            return stops;
        }
        List<RouteStop> reversed = new ArrayList<>(stops);
        for (int i = 0, j = reversed.size() - 1; i < j; i++, j--) {
            RouteStop tmp = reversed.get(i);
            reversed.set(i, reversed.get(j));
            reversed.set(j, tmp);
        }
        return reversed;
    }

    /** Map oriented route stops to StopDtos with renumbered travel order. */
    private List<PassengerDtos.StopDto> stopDtos(List<RouteStop> routeStops) {
        List<PassengerDtos.StopDto> stops = new ArrayList<>(routeStops.size());
        for (int i = 0; i < routeStops.size(); i++) {
            RouteStop s = routeStops.get(i);
            stops.add(new PassengerDtos.StopDto(
                    i + 1, s.getStopName(), s.getLatitude(), s.getLongitude(),
                    s.getDistanceFromStart()));
        }
        return stops;
    }

    /**
     * Resolve the direction of travel implied by a from/to search against a
     * route stored in natural (OUTBOUND) stop order. Forward when the from-stop
     * appears before the to-stop, reverse (INBOUND) when it appears after.
     * Falls back to terminal names when both stops are not found in the stop
     * list. Returns {@code null} when no relationship can be resolved (the
     * route matches a stop neither in its endpoints nor its stop list).
     */
    private String resolveDirection(Route route, String from, String to) {
        List<RouteStop> stops = routeStopRepository.findByRouteIdOrderByStopOrder(route.getId());
        String f = norm(from);
        String t = norm(to);
        int fromIdx = -1;
        int toIdx = -1;
        for (int i = 0; i < stops.size(); i++) {
            if (stops.get(i).getStopName() == null) {
                continue;
            }
            String n = norm(stops.get(i).getStopName());
            if (f.length() > 0 && n.contains(f)) {
                fromIdx = i;
            }
            if (t.length() > 0 && n.contains(t)) {
                toIdx = i;
            }
        }
        if (fromIdx >= 0 && toIdx >= 0 && fromIdx != toIdx) {
            return fromIdx < toIdx ? "OUTBOUND" : "INBOUND";
        }
        boolean fromInOrigin = route.getOrigin() != null && norm(route.getOrigin()).contains(f);
        boolean fromInDest = route.getDestination() != null && norm(route.getDestination()).contains(f);
        boolean toInOrigin = route.getOrigin() != null && norm(route.getOrigin()).contains(t);
        boolean toInDest = route.getDestination() != null && norm(route.getDestination()).contains(t);
        if (fromInOrigin && toInDest) {
            return "OUTBOUND";
        }
        if (fromInDest && toInOrigin) {
            return "INBOUND";
        }
        return null;
    }

    /**
     * Corridor-oriented RouteDto for the passenger map: stops are sliced to the
     * searched from→to segment (in travel order) so the map fits the relevant
     * area, while terminals reflect the travel direction.
     */
    private PassengerDtos.RouteDto corridorRouteDto(Route route, List<RouteStop> rawStops, String direction,
                                                    String fromName, String toName, String fromRaw, String toRaw) {
        List<RouteStop> oriented = oriented(rawStops, direction);
        List<PassengerDtos.StopDto> stops = stopDtos(oriented);
        if (stops.isEmpty()) {
            return toRouteDto(route, stops, direction);
        }
        int fromIdx = indexOfMatch(oriented, fromName, fromRaw);
        int toIdx = indexOfMatch(oriented, toName, toRaw);
        if (fromIdx < 0) {
            fromIdx = 0;
        }
        if (toIdx < 0) {
            toIdx = stops.size() - 1;
        }
        if (fromIdx > toIdx) {
            int tmp = fromIdx;
            fromIdx = toIdx;
            toIdx = tmp;
        }
        List<PassengerDtos.StopDto> segment = stops.subList(fromIdx, toIdx + 1);
        return new PassengerDtos.RouteDto(
                route.getId(), route.getCode(), route.getName(),
                originFor(route, direction), destinationFor(route, direction),
                direction, segment);
    }

    private int indexOfMatch(List<RouteStop> ordered, String canonical, String raw) {
        String c = norm(canonical);
        String r = norm(raw);
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).getStopName() == null) {
                continue;
            }
            String n = norm(ordered.get(i).getStopName());
            if ((!c.isEmpty() && n.equals(c)) || (!r.isEmpty() && n.equals(r))) {
                return i;
            }
        }
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).getStopName() == null) {
                continue;
            }
            String n = norm(ordered.get(i).getStopName());
            if ((!c.isEmpty() && n.contains(c)) || (!r.isEmpty() && n.contains(r))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Best official stop/terminal name for a user-supplied search input:
     * exact stop match, exact terminal match, then the shortest stop
     * containing the input, then terminals, finally the input unchanged.
     */
    private String bestStopName(List<RouteStop> stops, Route route, String input) {
        String term = norm(input);
        if (term.isEmpty()) {
            return input;
        }
        for (RouteStop s : stops) {
            if (s.getStopName() != null && norm(s.getStopName()).equals(term)) {
                return s.getStopName();
            }
        }
        if (route.getOrigin() != null && norm(route.getOrigin()).equals(term)) {
            return route.getOrigin();
        }
        if (route.getDestination() != null && norm(route.getDestination()).equals(term)) {
            return route.getDestination();
        }
        RouteStop best = null;
        for (RouteStop s : stops) {
            if (s.getStopName() != null && norm(s.getStopName()).contains(term)) {
                if (best == null || s.getStopName().length() < best.getStopName().length()) {
                    best = s;
                }
            }
        }
        if (best != null) {
            return best.getStopName();
        }
        if (route.getOrigin() != null && norm(route.getOrigin()).contains(term)) {
            return route.getOrigin();
        }
        if (route.getDestination() != null && norm(route.getDestination()).contains(term)) {
            return route.getDestination();
        }
        return input;
    }

    // ------------------------------------------------------------------
    // connecting journeys
    // ------------------------------------------------------------------

    private static final int MAX_CONNECT_LEGS = 3;          // 0, 1 or 2 transfers
    private static final double TRANSFER_KM_TOLERANCE = 2.0; // same physical stop
    private static final int MAX_CONNECT_CANDIDATES = 40;   // explored-branch cap
    private static final int MAX_CONNECT_RESULTS = 6;       // returned journeys
    private static final int MAX_DFS_EXPANSIONS = 5000;     // worst-case guard

    private record LegRef(Long routeId, String direction, int fromIdx, int toIdx) {
    }

    private record PathRef(List<LegRef> legs, Set<String> covered) {
    }

    private record LegTimes(LocalDateTime board, LocalDateTime alight) {
    }

    /**
     * Connecting journeys from the real {@code route_stops} graph (never
     * synthesised). A journey has 1 or 2 transfers and is a valid sequence of
     * legs over distinct enabled ACTIVE routes, joined at real shared stops
     * (same normalized name within the transfer tolerance). Direct single-route
     * results are NOT emitted here (they stay in SearchResult.buses/routes).
     */
    private List<PassengerDtos.ConnectingJourney> findConnecting(
            String fromRaw, String toRaw, String fromNorm, String toNorm) {
        List<Route> routes = routeRepository.findAllEnabledActive();
        if (routes == null || routes.isEmpty()) {
            return List.of();
        }
        Map<Long, Route> routeById = new HashMap<>();
        List<Long> routeIds = new ArrayList<>(routes.size());
        for (Route r : routes) {
            routeById.put(r.getId(), r);
            routeIds.add(r.getId());
        }
        Map<Long, List<RouteStop>> stopsByRoute = new HashMap<>();
        for (RouteStop s : routeStopRepository.findByRouteIdIn(routeIds)) {
            stopsByRoute.computeIfAbsent(s.getRoute().getId(), k -> new ArrayList<>()).add(s);
        }

        // Real transfer points: a stop that appears on two different routes at
        // virtually the same location.
        Set<String> junctionStops = junctionStops(stopsByRoute);

        // DFS over the route graph, bounded.
        List<List<LegRef>> candidates = new ArrayList<>();
        dfs(new PathRef(List.of(), Set.of()), stopsByRoute, junctionStops,
                fromNorm, toNorm, candidates, new int[]{MAX_DFS_EXPANSIONS});
        if (candidates.isEmpty()) {
            return List.of();
        }

        List<Long> usedRouteIds = candidates.stream()
                .flatMap(List::stream).map(LegRef::routeId).distinct().toList();
        var trips = tripRepository.findByRouteIdInAndStatus(usedRouteIds, TripStatus.RUNNING);
        Set<Long> busIds = trips.stream().map(t -> t.getBus().getId()).collect(Collectors.toSet());
        var liveByBus = liveLocationRepository.findByBusIdIn(busIds.isEmpty()
                        ? List.of(-1L) : new ArrayList<>(busIds))
                .stream().collect(Collectors.toMap(l -> l.getBus().getId(), l -> l, (a, b) -> a));

        int minTransfer = Math.max(settings.searchMinTransferMinutes(), 0);

        List<PassengerDtos.ConnectingJourney> journeys = new ArrayList<>();
        for (List<LegRef> legs : candidates) {
            PassengerDtos.ConnectingJourney journey = toJourney(
                    legs, routeById, stopsByRoute, trips, liveByBus, minTransfer);
            if (journey != null) {
                journeys.add(journey);
            }
        }
        journeys.sort(Comparator
                .comparingInt(PassengerDtos.ConnectingJourney::numberOfTransfers)
                .thenComparingLong(PassengerService::totalJourneyStops)
                .thenComparingLong(PassengerService::journeyDurationSeconds)
                .thenComparingLong(PassengerService::transferWaitSeconds)
                .thenComparing(PassengerDtos.ConnectingJourney::from)
                .thenComparing(PassengerService::journeyKey));
        return journeys.size() > MAX_CONNECT_RESULTS
                ? new ArrayList<>(journeys.subList(0, MAX_CONNECT_RESULTS))
                : journeys;
    }

    private Set<String> junctionStops(Map<Long, List<RouteStop>> stopsByRoute) {
        Map<String, List<RouteStop>> occByNorm = new HashMap<>();
        for (List<RouteStop> list : stopsByRoute.values()) {
            for (RouteStop s : list) {
                if (s.getStopName() == null || s.getLatitude() == null || s.getLongitude() == null) {
                    continue;
                }
                String n = norm(s.getStopName());
                if (n.isEmpty()) {
                    continue;
                }
                occByNorm.computeIfAbsent(n, k -> new ArrayList<>()).add(s);
            }
        }
        Set<String> junctionStops = new HashSet<>();
        for (List<RouteStop> refs : occByNorm.values()) {
            nextName:
            for (int a = 0; a + 1 < refs.size(); a++) {
                for (int b = a + 1; b < refs.size(); b++) {
                    RouteStop x = refs.get(a);
                    RouteStop y = refs.get(b);
                    if (!x.getRoute().getId().equals(y.getRoute().getId())
                            && Geo.distanceKm(x.getLatitude(), x.getLongitude(),
                                    y.getLatitude(), y.getLongitude()) <= TRANSFER_KM_TOLERANCE) {
                        junctionStops.add(norm(x.getStopName()));
                        break nextName;
                    }
                }
            }
        }
        return junctionStops;
    }

    private void dfs(PathRef path, Map<Long, List<RouteStop>> stopsByRoute, Set<String> junctionStops,
                     String fromNorm, String toNorm, List<List<LegRef>> candidates, int[] budget) {
        if (budget[0] <= 0 || candidates.size() >= MAX_CONNECT_CANDIDATES) {
            return;
        }
        budget[0] = budget[0] - 1;

        int n = path.legs().size();
        if (n > 0) {
            LegRef last = path.legs().get(n - 1);
            List<RouteStop> stops = stopsByRoute.get(last.routeId());
            String alight = norm(stops.get(last.toIdx()).getStopName());
            if (matchesTo(alight, toNorm)) {
                // A journey reaching the destination needs at least one transfer.
                if (n >= 2) {
                    candidates.add(path.legs());
                }
                return;
            }
        }
        if (n >= MAX_CONNECT_LEGS) {
            return;
        }

        if (n == 0) {
            // First leg: board anywhere matching FROM (same contains semantics
            // as the direct search), ride to any other stop on that route.
            for (Map.Entry<Long, List<RouteStop>> e : stopsByRoute.entrySet()) {
                List<RouteStop> list = e.getValue();
                for (int i = 0; i < list.size(); i++) {
                    String board = norm(list.get(i).getStopName());
                    if (board.isEmpty() || !matchesFrom(board, fromNorm)) {
                        continue;
                    }
                    for (int j = 0; j < list.size(); j++) {
                        if (j == i) {
                            continue;
                        }
                        String dir = i < j ? "OUTBOUND" : "INBOUND";
                        LegRef leg = new LegRef(e.getKey(), dir, i, j);
                        Set<String> covered = new HashSet<>();
                        addCovered(covered, list, i, j);
                        dfs(new PathRef(List.of(leg), covered), stopsByRoute, junctionStops,
                                fromNorm, toNorm, candidates, budget);
                    }
                }
            }
            return;
        }

        // Subsequent leg: transfer at a real junction to a fresh route, ride to
        // a terminal that is not already covered (unless it is the destination).
        LegRef last = path.legs().get(n - 1);
        List<RouteStop> lastStops = stopsByRoute.get(last.routeId());
        String alightNorm = norm(lastStops.get(last.toIdx()).getStopName());
        Set<Long> usedRoutes = path.legs().stream().map(LegRef::routeId).collect(Collectors.toSet());
        for (Map.Entry<Long, List<RouteStop>> e : stopsByRoute.entrySet()) {
            Long routeId = e.getKey();
            if (usedRoutes.contains(routeId)) {
                continue;
            }
            List<RouteStop> list = e.getValue();
            for (int k = 0; k < list.size(); k++) {
                String board = norm(list.get(k).getStopName());
                if (board.isEmpty() || !board.equals(alightNorm) || !junctionStops.contains(board)) {
                    continue;
                }
                for (int j = 0; j < list.size(); j++) {
                    if (j == k) {
                        continue;
                    }
                    String term = norm(list.get(j).getStopName());
                    if (term.isEmpty()) {
                        continue;
                    }
                    if (interiorBlocked(list, k, j, path.covered()) && !matchesTo(term, toNorm)) {
                        continue;
                    }
                    String dir = k < j ? "OUTBOUND" : "INBOUND";
                    List<LegRef> nextLegs = new ArrayList<>(path.legs());
                    nextLegs.add(new LegRef(routeId, dir, k, j));
                    Set<String> covered = new HashSet<>(path.covered());
                    addCovered(covered, list, k, j);
                    dfs(new PathRef(nextLegs, covered), stopsByRoute, junctionStops,
                            fromNorm, toNorm, candidates, budget);
                }
            }
        }
    }

    private static boolean matchesFrom(String stopNorm, String fromNorm) {
        if (stopNorm.isEmpty() || fromNorm.isEmpty()) {
            return false;
        }
        return stopNorm.equals(fromNorm) || stopNorm.contains(fromNorm);
    }

    private static boolean matchesTo(String stopNorm, String toNorm) {
        if (stopNorm.isEmpty() || toNorm.isEmpty()) {
            return false;
        }
        return stopNorm.equals(toNorm) || stopNorm.contains(toNorm);
    }

    private static void addCovered(Set<String> covered, List<RouteStop> list, int fromIdx, int toIdx) {
        int lo = Math.min(fromIdx, toIdx);
        int hi = Math.max(fromIdx, toIdx);
        for (int i = lo; i <= hi; i++) {
            String nm = norm(list.get(i).getStopName());
            if (!nm.isEmpty()) {
                covered.add(nm);
            }
        }
    }

    private static boolean interiorBlocked(List<RouteStop> list, int fromIdx, int toIdx, Set<String> covered) {
        int lo = Math.min(fromIdx, toIdx) + 1;
        int hi = Math.max(fromIdx, toIdx) - 1;
        for (int i = lo; i <= hi; i++) {
            String nm = norm(list.get(i).getStopName());
            if (!nm.isEmpty() && covered.contains(nm)) {
                return true;
            }
        }
        return false;
    }

    private static List<Trip> legTrips(List<Trip> trips, LegRef leg) {
        return trips.stream()
                .filter(t -> t.getRoute() != null && t.getRoute().getId().equals(leg.routeId())
                        && legDirectionMatches(t, leg.direction()))
                .toList();
    }

    private static boolean legDirectionMatches(Trip trip, String direction) {
        String d = trip.getDirection() == null ? "OUTBOUND" : trip.getDirection();
        String required = direction == null ? "OUTBOUND" : direction;
        return d.equalsIgnoreCase(required);
    }

    /** Best-effort boarding/alighting times from a leg's earliest scheduled trip. */
    private LegTimes estimateLegTime(Trip trip, List<RouteStop> stops, LegRef leg) {
        int last = stops.size() - 1;
        if (stops.size() < 2
                || trip.getScheduledDeparture() == null || trip.getScheduledArrival() == null) {
            return null;
        }
        long totalSecs = Duration.between(trip.getScheduledDeparture(), trip.getScheduledArrival()).getSeconds();
        if (totalSecs <= 0) {
            return null;
        }
        boolean inbound = "INBOUND".equalsIgnoreCase(leg.direction());
        double boardFrac = inbound ? (double) (last - leg.fromIdx()) / last
                : (double) leg.fromIdx() / last;
        double alightFrac = inbound ? (double) (last - leg.toIdx()) / last
                : (double) leg.toIdx() / last;
        LocalDateTime base = trip.getScheduledDeparture();
        return new LegTimes(
                base.plusSeconds((long) (boardFrac * totalSecs)),
                base.plusSeconds((long) (alightFrac * totalSecs)));
    }

    private PassengerDtos.ConnectingJourney toJourney(List<LegRef> legs, Map<Long, Route> routeById,
                                                      Map<Long, List<RouteStop>> stopsByRoute,
                                                      List<Trip> trips, Map<Long, LiveLocation> liveByBus,
                                                      int minTransfer) {
        int n = legs.size();
        for (LegRef leg : legs) {
            List<RouteStop> stops = stopsByRoute.get(leg.routeId());
            if (stops == null || leg.fromIdx() < 0 || leg.toIdx() < 0
                    || leg.fromIdx() >= stops.size() || leg.toIdx() >= stops.size()
                    || leg.fromIdx() == leg.toIdx()) {
                return null;
            }
        }

        // Transfer timing: only enforced when scheduled trip data exists for
        // both consecutive legs. Legs without trips are kept (service exists).
        List<LegTimes> times = new ArrayList<>(n);
        for (LegRef leg : legs) {
            List<Trip> legTrips = legTrips(trips, leg);
            Trip earliest = legTrips.stream()
                    .min(Comparator.comparing(Trip::getScheduledDeparture))
                    .orElse(null);
            times.add(earliest == null ? null
                    : estimateLegTime(earliest, stopsByRoute.get(leg.routeId()), leg));
        }
        for (int k = 0; k + 1 < n; k++) {
            LegTimes a = times.get(k);
            LegTimes b = times.get(k + 1);
            if (a != null && b != null && b.board.isBefore(a.alight.plusMinutes(minTransfer))) {
                return null;
            }
        }

        List<PassengerDtos.JourneyLeg> legDtos = new ArrayList<>(n);
        List<String> transferStops = new ArrayList<>(Math.max(n - 1, 0));
        for (int legIndex = 0; legIndex < n; legIndex++) {
            LegRef leg = legs.get(legIndex);
            Route route = routeById.get(leg.routeId());
            List<RouteStop> stops = stopsByRoute.get(leg.routeId());
            List<Trip> legTrips = legTrips(trips, leg);
            String boardingName = stops.get(leg.fromIdx()).getStopName();
            String alightName = stops.get(leg.toIdx()).getStopName();

            List<PassengerDtos.BusSummary> buses = new ArrayList<>(legTrips.size());
            for (Trip trip : legTrips) {
                LiveLocation live = liveByBus.get(trip.getBus().getId());
                buses.add(toSummary(trip.getBus(), route, leg.direction(), live));
            }
            buses.sort(Comparator
                    .comparingInt((PassengerDtos.BusSummary s) -> s.status() == LiveStatus.OFFLINE ? 1 : 0)
                    .thenComparing(PassengerDtos.BusSummary::registrationNo));

            LegTimes lt = times.get(legIndex);
            PassengerDtos.RouteDto legRoute = corridorRouteDto(route, stops, leg.direction(),
                    boardingName, alightName, boardingName, alightName);
            legDtos.add(new PassengerDtos.JourneyLeg(
                    legIndex + 1,
                    leg.direction(),
                    boardingName,
                    alightName,
                    legIndex == 0 ? null : boardingName,
                    lt == null ? null : lt.board,
                    lt == null ? null : lt.alight,
                    legRoute,
                    buses));
            if (legIndex > 0) {
                transferStops.add(stopsByRoute.get(legs.get(legIndex - 1).routeId())
                        .get(legs.get(legIndex - 1).toIdx()).getStopName());
            }
        }

        String from = legs.isEmpty() ? null
                : stopsByRoute.get(legs.get(0).routeId()).get(legs.get(0).fromIdx()).getStopName();
        String to = legs.isEmpty() ? null
                : stopsByRoute.get(legs.get(n - 1).routeId()).get(legs.get(n - 1).toIdx()).getStopName();
        return new PassengerDtos.ConnectingJourney(
                from, to, "CONNECTING", n - 1, transferStops, legDtos);
    }

    private static long totalJourneyStops(PassengerDtos.ConnectingJourney j) {
        return j.legs().stream()
                .mapToLong(l -> l.route() == null || l.route().stops() == null
                        ? 0 : Math.max(l.route().stops().size() - 1, 0))
                .sum();
    }

    private static long journeyDurationSeconds(PassengerDtos.ConnectingJourney j) {
        if (j.legs().isEmpty()) {
            return Long.MAX_VALUE;
        }
        LocalDateTime start = j.legs().get(0).plannedDeparture();
        LocalDateTime end = j.legs().get(j.legs().size() - 1).plannedArrival();
        if (start == null || end == null) {
            return Long.MAX_VALUE;
        }
        return Duration.between(start, end).getSeconds();
    }

    private static long transferWaitSeconds(PassengerDtos.ConnectingJourney j) {
        long total = 0;
        for (int i = 1; i < j.legs().size(); i++) {
            LocalDateTime prevArr = j.legs().get(i - 1).plannedArrival();
            LocalDateTime nextDep = j.legs().get(i).plannedDeparture();
            if (prevArr != null && nextDep != null) {
                total += Duration.between(prevArr, nextDep).getSeconds();
            }
        }
        return total;
    }

    private static String journeyKey(PassengerDtos.ConnectingJourney j) {
        StringBuilder sb = new StringBuilder();
        for (PassengerDtos.JourneyLeg l : j.legs()) {
            sb.append(l.from()).append('>').append(l.to()).append(';');
        }
        return sb.toString();
    }

    /**
     * Canonical normalization for stop matching: NFKC, trim, collapse repeated
     * whitespace, lowercase. Used for both user input and stored names so the
     * same text ("Gokarna Road" vs " gokarna  road ") always matches.
     */
    static String norm(String s) {
        if (s == null) {
            return "";
        }
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFKC)
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    @Transactional(readOnly = true)
    public List<PassengerDtos.BusSummary> allLiveBuses(int limit) {
        int max = Math.min(limit <= 0 ? 200 : limit, 500);
        return liveLocationRepository.findAllByOrderByCapturedAtDesc().stream()
                .limit(max)
                .map(l -> toSummary(l.getBus(), l.getTrip() == null ? null : l.getTrip().getRoute(),
                        l.getTrip() == null ? "OUTBOUND" : (l.getTrip().getDirection() == null ? "OUTBOUND" : l.getTrip().getDirection()), l))
                .toList();
    }
}
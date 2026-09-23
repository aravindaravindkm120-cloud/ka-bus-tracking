package com.kabus.tracking.api;

import com.kabus.tracking.service.PassengerService;
import com.kabus.tracking.service.RouteGeometryService;
import com.kabus.tracking.web.dto.PassengerDtos;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Public read-only passenger API. No authentication required. Rate limited
 * per IP (see RateLimitFilter).
 */
@RestController
@RequestMapping("/api/public")
public class PassengerController {

    private final PassengerService passengerService;
    private final RouteGeometryService routeGeometryService;

    public PassengerController(PassengerService passengerService,
                               RouteGeometryService routeGeometryService) {
        this.passengerService = passengerService;
        this.routeGeometryService = routeGeometryService;
    }

    @GetMapping("/config")
    public ResponseEntity<PassengerDtos.PublicConfig> config() {
        return ResponseEntity.ok(passengerService.publicConfig());
    }

    @GetMapping("/stops")
    public ResponseEntity<List<PassengerDtos.StopSuggestion>> stopSuggestions(@RequestParam String term,
                                                                              @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(passengerService.stopSuggestions(term, limit));
    }

    @GetMapping("/routes")
    public ResponseEntity<List<PassengerDtos.RouteDto>> routeSuggestions(@RequestParam String term,
                                                                         @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(passengerService.routeSuggestions(term, limit));
    }

    @GetMapping("/search")
    public ResponseEntity<PassengerDtos.SearchResult> search(@RequestParam String from,
                                                             @RequestParam String to,
                                                             @RequestParam(defaultValue = "0") int page,
                                                             @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(passengerService.search(from, to, page, size));
    }

    @GetMapping("/buses")
    public ResponseEntity<Page<PassengerDtos.BusSummary>> buses(@RequestParam(defaultValue = "0") int page,
                                                                @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(passengerService.liveBusPage(page, size));
    }

    @GetMapping("/buses/nearby")
    public ResponseEntity<PassengerDtos.NearbyResult> nearby(@RequestParam BigDecimal latitude,
                                                             @RequestParam BigDecimal longitude,
                                                             @RequestParam(required = false) BigDecimal radiusKm,
                                                             @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(passengerService.nearby(latitude, longitude, radiusKm, limit));
    }

    @GetMapping("/buses/{busId}")
    public ResponseEntity<PassengerDtos.BusDetails> busDetails(@PathVariable Long busId) {
        return ResponseEntity.ok(passengerService.busDetails(busId));
    }

    @GetMapping("/routes/{routeId}/geometry")
    public ResponseEntity<PassengerDtos.RouteGeometryDto> routeGeometry(
            @PathVariable Long routeId,
            @RequestParam(defaultValue = "OUTBOUND") String direction) {
        return ResponseEntity.ok(routeGeometryService.geometry(routeId, direction));
    }

    @GetMapping("/routes/{routeId}")
    public ResponseEntity<PassengerDtos.RouteDto> routeDetails(
            @PathVariable Long routeId,
            @RequestParam(defaultValue = "OUTBOUND") String direction) {
        return ResponseEntity.ok(passengerService.routeDetails(routeId, direction));
    }

    @GetMapping("/live")
    public ResponseEntity<List<PassengerDtos.BusSummary>> liveBuses(
            @RequestParam(defaultValue = "200") int limit) {
        return ResponseEntity.ok(passengerService.allLiveBuses(limit));
    }

    @PostMapping("/searches")
    public ResponseEntity<Void> recordSearch(@RequestHeader(value = "X-Device-Id", required = false) String deviceId,
                                             @RequestBody PassengerDtos.SearchRequest request) {
        passengerService.recordSearch(deviceId, request.from(), request.to());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/searches")
    public ResponseEntity<List<PassengerDtos.RecentSearchItem>> recentSearches(
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId) {
        return ResponseEntity.ok(passengerService.recentSearches(deviceId));
    }

    @GetMapping("/favorites")
    public ResponseEntity<List<PassengerDtos.FavoriteItem>> favorites(
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId) {
        return ResponseEntity.ok(passengerService.favorites(deviceId));
    }

    @PostMapping("/favorites")
    public ResponseEntity<PassengerDtos.FavoriteItem> addFavorite(
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            @RequestBody PassengerDtos.FavoriteItem item) {
        var saved = passengerService.addFavorite(deviceId, item.itemType(), item.itemId(),
                item.label(), item.latitude(), item.longitude());
        return ResponseEntity.ok(new PassengerDtos.FavoriteItem(
                saved.getId(), saved.getItemType(), saved.getItemId(), saved.getLabel(),
                saved.getLatitude(), saved.getLongitude()));
    }

    @DeleteMapping("/favorites/{itemType}/{itemId}")
    public ResponseEntity<Void> removeFavorite(
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            @PathVariable String itemType, @PathVariable Long itemId) {
        passengerService.removeFavorite(deviceId, itemType, itemId);
        return ResponseEntity.noContent().build();
    }
}
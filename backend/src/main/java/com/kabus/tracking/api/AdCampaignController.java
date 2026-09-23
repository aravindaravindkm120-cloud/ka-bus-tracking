package com.kabus.tracking.api;

import com.kabus.tracking.security.UserPrincipal;
import com.kabus.tracking.service.AdService;
import com.kabus.tracking.web.dto.AdDtos;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Ad campaign management - restricted to SUPER_ADMIN (enforced server-side in
 * AdService, so division-level admins are denied even if they call directly).
 * Serving/recording impressions are public endpoints under /api/public/ad/**.
 */
@RestController
@RequestMapping("/api/admin/ads")
public class AdCampaignController {

    private final AdService adService;

    public AdCampaignController(AdService adService) {
        this.adService = adService;
    }

    @GetMapping("/placements")
    public ResponseEntity<List<AdDtos.PlacementInfo>> placements(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(adService.placements(principal));
    }

    @GetMapping("/campaigns")
    public ResponseEntity<Page<AdDtos.CampaignResponse>> campaigns(@AuthenticationPrincipal UserPrincipal principal,
                                                                   @RequestParam(defaultValue = "0") int page,
                                                                   @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adService.campaigns(principal,
                Math.max(page, 0), Math.min(Math.max(size, 1), 50)));
    }

    @PostMapping("/campaigns")
    public ResponseEntity<AdDtos.CampaignResponse> create(@AuthenticationPrincipal UserPrincipal principal,
                                                          @Valid @RequestBody AdDtos.CampaignRequest request) {
        return ResponseEntity.ok(adService.createCampaign(principal, request));
    }

    @PutMapping("/campaigns/{campaignId}")
    public ResponseEntity<AdDtos.CampaignResponse> update(@AuthenticationPrincipal UserPrincipal principal,
                                                          @PathVariable Long campaignId,
                                                          @Valid @RequestBody AdDtos.CampaignRequest request) {
        return ResponseEntity.ok(adService.updateCampaign(principal, campaignId, request));
    }

    @PatchMapping("/campaigns/{campaignId}/enabled")
    public ResponseEntity<Void> setEnabled(@AuthenticationPrincipal UserPrincipal principal,
                                           @PathVariable Long campaignId,
                                           @RequestParam boolean enabled) {
        adService.setCampaignEnabled(principal, campaignId, enabled);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/campaigns/{campaignId}/stats")
    public ResponseEntity<AdDtos.CampaignStats> stats(@AuthenticationPrincipal UserPrincipal principal,
                                                      @PathVariable Long campaignId) {
        return ResponseEntity.ok(adService.campaignStats(principal, campaignId));
    }
}
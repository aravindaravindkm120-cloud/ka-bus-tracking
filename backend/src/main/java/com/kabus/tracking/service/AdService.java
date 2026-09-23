package com.kabus.tracking.service;

import com.kabus.tracking.domain.entity.AdCampaign;
import com.kabus.tracking.domain.entity.AdImpression;
import com.kabus.tracking.domain.entity.AdPlacement;
import com.kabus.tracking.domain.enums.AdPlacementCode;
import com.kabus.tracking.domain.enums.RoleCode;
import com.kabus.tracking.domain.repository.AdCampaignRepository;
import com.kabus.tracking.domain.repository.AdImpressionRepository;
import com.kabus.tracking.domain.repository.AdPlacementRepository;
import com.kabus.tracking.domain.repository.UserRepository;
import com.kabus.tracking.exception.ApiException;
import com.kabus.tracking.security.UserPrincipal;
import com.kabus.tracking.web.dto.AdDtos;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Centralized ad system.
 *
 * <p>Only SUPER_ADMIN may create/modify campaigns or placements, and only
 * when explicitly enabled. Division-level and lower administrators are
 * blocked server-side. Impressions are recorded only when a client actually
 * reports that an ad was displayed - no fake impressions are generated.</p>
 */
@Service
public class AdService {

    private final AdCampaignRepository campaignRepository;
    private final AdPlacementRepository placementRepository;
    private final AdImpressionRepository impressionRepository;
    private final UserRepository userRepository;

    public AdService(AdCampaignRepository campaignRepository,
                     AdPlacementRepository placementRepository,
                     AdImpressionRepository impressionRepository,
                     UserRepository userRepository) {
        this.campaignRepository = campaignRepository;
        this.placementRepository = placementRepository;
        this.impressionRepository = impressionRepository;
        this.userRepository = userRepository;
    }

    // ------------------------------------------------------------------
    // Serving (public/passenger + admin/crew)
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public AdDtos.ServeAdResponse serve(String placementCode) {
        if (!AdPlacementCode.isSupported(placementCode)) {
            throw ApiException.badRequest("Unknown ad placement: " + placementCode);
        }
        AdPlacement placement = placementRepository.findByCode(placementCode)
                .orElseThrow(() -> ApiException.notFound("Ad placement not configured: " + placementCode));
        LocalDateTime now = LocalDateTime.now();

        List<AdCampaign> eligible = campaignRepository.findEligibleForPlacement(placementCode, now);
        if (eligible.isEmpty()) {
            eligible = campaignRepository.findEligibleForPlacementNoCap(placementCode, now);
        }
        if (eligible.isEmpty()) {
            return new AdDtos.ServeAdResponse(null, null, null, null, null, -1, -1);
        }
        AdCampaign campaign = eligible.get(0);
        String adId = campaign.getId() + ":" + placementCode;
        return new AdDtos.ServeAdResponse(
                adId, campaign.getId(), campaign.getTitle(), campaign.getImageUrl(),
                campaign.getTargetUrl(),
                placement.getDurationSeconds() != null ? placement.getDurationSeconds() : 5,
                placement.getFrequencySeconds() != null ? placement.getFrequencySeconds() : 0);
    }

    @Transactional
    public void recordImpression(AdDtos.ImpressionRequest request) {
        Long campaignId = campaignIdFrom(request.adId());
        String placementFromAd = placementFrom(request.adId());
        if (campaignId == null || !request.placement().equals(placementFromAd)) {
            // Reject malformed or tampered ad-ids - they must match the placement.
            return;
        }
        AdCampaign campaign = campaignRepository.findById(campaignId).orElse(null);
        AdPlacement placement = placementRepository.findByCode(request.placement()).orElse(null);
        if (campaign == null || placement == null || !placement.isEnabled()) {
            return;
        }

        AdImpression impression = new AdImpression();
        impression.setCampaign(campaign);
        impression.setPlacement(placement);
        impression.setDeviceId(request.deviceId() == null ? null
                : request.deviceId().substring(0, Math.min(request.deviceId().length(), 80)));
        impression.setDurationViewedMs(Math.max(0, request.durationViewedMs()));
        impression.setClicked(request.clicked());
        impression.setViewedAt(LocalDateTime.now());
        impressionRepository.save(impression);

        // Only a genuine view (non-zero duration) counts toward impressions.
        if (request.durationViewedMs() > 0) {
            campaignRepository.incrementImpressions(campaign.getId());
        }
    }

    private Long campaignIdFrom(String adId) {
        if (adId == null || !adId.contains(":")) {
            return null;
        }
        try {
            return Long.parseLong(adId.split(":")[0]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String placementFrom(String adId) {
        if (adId == null || !adId.contains(":")) {
            return null;
        }
        return adId.substring(adId.indexOf(':') + 1);
    }

    // ------------------------------------------------------------------
    // Management (SUPER_ADMIN only)
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<AdDtos.CampaignResponse> campaigns(UserPrincipal principal, int page, int size) {
        requireSuperAdmin(principal);
        return campaignRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size))
                .map(this::toCampaignResponse);
    }

    @Transactional(readOnly = true)
    public AdDtos.CampaignStats campaignStats(UserPrincipal principal, Long campaignId) {
        requireSuperAdmin(principal);
        // A "genuine" impression requires a real, non-zero view duration.
        long impressions = impressionRepository.countByCampaignIdAndDurationViewedMsGreaterThan(campaignId, 0);
        long clicks = impressionRepository.countByCampaignIdAndClickedTrue(campaignId);
        double ctr = impressions == 0 ? 0 : (double) clicks / impressions;
        return new AdDtos.CampaignStats(impressions, clicks, ctr);
    }

    @Transactional(readOnly = true)
    public List<AdDtos.PlacementInfo> placements(UserPrincipal principal) {
        if (!principal.isSuperAdmin()) {
            throw ApiException.forbidden("Only SUPER_ADMIN can view ad configuration.");
        }
        return placementRepository.findAll().stream()
                .map(p -> new AdDtos.PlacementInfo(p.getCode(), p.getName(),
                        p.getDurationSeconds(), p.getFrequencySeconds(), p.isEnabled()))
                .toList();
    }

    @Transactional
    public AdDtos.CampaignResponse createCampaign(UserPrincipal principal, AdDtos.CampaignRequest request) {
        requireSuperAdmin(principal);
        AdCampaign campaign = new AdCampaign();
        apply(campaign, request);
        campaign.setCreatedBy(userRepository.findById(principal.getUserId()).orElse(null));
        campaign = campaignRepository.save(campaign);
        return toCampaignResponse(campaign);
    }

    @Transactional
    public AdDtos.CampaignResponse updateCampaign(UserPrincipal principal, Long campaignId,
                                                  AdDtos.CampaignRequest request) {
        requireSuperAdmin(principal);
        AdCampaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> ApiException.notFound("Campaign not found."));
        apply(campaign, request);
        campaignRepository.save(campaign);
        return toCampaignResponse(campaign);
    }

    @Transactional
    public void setCampaignEnabled(UserPrincipal principal, Long campaignId, boolean enabled) {
        requireSuperAdmin(principal);
        AdCampaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> ApiException.notFound("Campaign not found."));
        campaign.setEnabled(enabled);
        campaignRepository.save(campaign);
    }

    private void apply(AdCampaign campaign, AdDtos.CampaignRequest request) {
        if (!AdPlacementCode.isSupported(request.placement())) {
            throw ApiException.badRequest("Unknown ad placement: " + request.placement());
        }
        if (request.endAt() != null && !request.endAt().isAfter(request.startAt())) {
            throw ApiException.badRequest("endAt must be after startAt.");
        }
        campaign.setTitle(request.title());
        campaign.setImageUrl(request.imageUrl());
        campaign.setTargetUrl(request.targetUrl());
        campaign.setPlacement(request.placement());
        campaign.setStartAt(request.startAt());
        campaign.setEndAt(request.endAt());
        campaign.setEnabled(request.enabled() == null || request.enabled());
        campaign.setPriority(request.priority() == null ? 0 : request.priority());
        campaign.setMaxImpressions(request.maxImpressions());
    }

    private AdDtos.CampaignResponse toCampaignResponse(AdCampaign c) {
        return new AdDtos.CampaignResponse(
                c.getId(), c.getTitle(), c.getImageUrl(), c.getTargetUrl(),
                c.getPlacement(), c.getStartAt(), c.getEndAt(), c.isEnabled(),
                c.getPriority(), c.getMaxImpressions(), c.getImpressionsCount(), c.getCreatedAt());
    }

    private void requireSuperAdmin(UserPrincipal principal) {
        if (!principal.hasRole(RoleCode.SUPER_ADMIN)) {
            throw ApiException.forbidden(
                    "Only SUPER_ADMIN may manage ads. Division-level roles are not authorized.");
        }
    }
}
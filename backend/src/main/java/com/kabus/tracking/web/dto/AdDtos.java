package com.kabus.tracking.web.dto;

import java.time.LocalDateTime;

/** DTOs for the centralized ad system. */
public final class AdDtos {

    private AdDtos() {
    }

    public record CampaignRequest(
            @jakarta.validation.constraints.NotBlank String title,
            @jakarta.validation.constraints.NotBlank String imageUrl,
            String targetUrl,
            @jakarta.validation.constraints.NotBlank String placement,
            @jakarta.validation.constraints.NotNull LocalDateTime startAt,
            LocalDateTime endAt,
            Boolean enabled,
            Integer priority,
            Long maxImpressions) {
    }

    public record CampaignResponse(
            Long id,
            String title,
            String imageUrl,
            String targetUrl,
            String placement,
            LocalDateTime startAt,
            LocalDateTime endAt,
            boolean enabled,
            Integer priority,
            Long maxImpressions,
            Long impressionsCount,
            LocalDateTime createdAt) {
    }

    /** A ready-to-display local ad-slot payload. No external ad provider is used. */
    public record ServeAdResponse(
            String adId,
            Long campaignId,
            String title,
            String imageUrl,
            String targetUrl,
            int durationSeconds,
            int frequencySeconds) {
    }

    public record ImpressionRequest(
            @jakarta.validation.constraints.NotBlank String adId,
            @jakarta.validation.constraints.NotBlank String placement,
            String deviceId,
            int durationViewedMs,
            boolean clicked) {
    }

    public record CampaignStats(
            long impressions,
            long clicks,
            double clickThroughRate) {
    }

    public record PlacementInfo(
            String code,
            String name,
            int durationSeconds,
            int frequencySeconds,
            boolean enabled) {
    }
}
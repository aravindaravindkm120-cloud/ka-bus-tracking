package com.kabus.tracking.domain.repository;

import com.kabus.tracking.domain.entity.AdImpression;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface AdImpressionRepository extends JpaRepository<AdImpression, Long> {

    long countByCampaignId(Long campaignId);

    long countByCampaignIdAndDurationViewedMsGreaterThan(Long campaignId, int minDurationMs);

    long countByCampaignIdAndClickedTrue(Long campaignId);

    long countByPlacementCodeAndViewedAtAfter(String placementCode, LocalDateTime after);

    Page<AdImpression> findByCampaignIdOrderByViewedAtDesc(Long campaignId, Pageable pageable);
}
package com.kabus.tracking.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ad_impressions")
@Getter
@Setter
@NoArgsConstructor
public class AdImpression extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    private AdCampaign campaign;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "placement_id", nullable = false)
    private AdPlacement placement;

    @Column(name = "device_id", length = 80)
    private String deviceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "viewed_at", nullable = false)
    private java.time.LocalDateTime viewedAt = java.time.LocalDateTime.now();

    @Column(name = "duration_viewed_ms", nullable = false)
    private Integer durationViewedMs = 0;

    @Column(name = "clicked", nullable = false)
    private boolean clicked = false;
}
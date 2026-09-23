package com.kabus.tracking.domain.repository;

import com.kabus.tracking.domain.entity.AdCampaign;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AdCampaignRepository extends JpaRepository<AdCampaign, Long> {

    List<AdCampaign> findByPlacementAndEnabledTrueAndStartAtLessThanEqualOrderByPriorityDesc(
            String placement, LocalDateTime now);

    Page<AdCampaign> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Optional<AdCampaign> findFirstByPlacementAndEnabledTrueAndStartAtLessThanEqualOrderByPriorityDescIdDesc(
            String placement, LocalDateTime now);

    @Query("select c from AdCampaign c where c.placement = :placement and c.enabled = true "
            + "and c.startAt <= :now and (c.endAt is null or c.endAt > :now) "
            + "and (c.maxImpressions is null or c.impressionsCount < c.maxImpressions) "
            + "order by c.priority desc, c.id desc")
    List<AdCampaign> findEligibleForPlacement(@Param("placement") String placement, @Param("now") LocalDateTime now);

    @Query("select c from AdCampaign c where c.placement = :placement and c.enabled = true "
            + "and c.startAt <= :now and (c.endAt is null or c.endAt > :now) "
            + "order by c.priority desc, c.id desc")
    List<AdCampaign> findEligibleForPlacementNoCap(@Param("placement") String placement, @Param("now") LocalDateTime now);

    @Modifying
    @Query("update AdCampaign c set c.impressionsCount = c.impressionsCount + 1 where c.id = :id")
    int incrementImpressions(@Param("id") Long id);
}
package com.kabus.tracking.domain.repository;

import com.kabus.tracking.domain.entity.Crew;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CrewRepository extends JpaRepository<Crew, Long> {

    Optional<Crew> findByUserId(Long userId);

    Optional<Crew> findByBadgeNo(String badgeNo);

    long countByStaffDepotIdIn(Collection<Long> depotIds);

    List<Crew> findByStaffDepotIdIn(Collection<Long> depotIds);

    long countByStaffTownIdIn(Collection<Long> townIds);

    List<Crew> findByStaffTownIdIn(Collection<Long> townIds);
}
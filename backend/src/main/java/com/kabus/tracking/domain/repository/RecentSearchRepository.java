package com.kabus.tracking.domain.repository;

import com.kabus.tracking.domain.entity.RecentSearch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecentSearchRepository extends JpaRepository<RecentSearch, Long> {

    List<RecentSearch> findTop10ByDeviceIdOrderByCreatedAtDesc(String deviceId);

    void deleteByDeviceIdAndFromNameAndToName(String deviceId, String fromName, String toName);
}
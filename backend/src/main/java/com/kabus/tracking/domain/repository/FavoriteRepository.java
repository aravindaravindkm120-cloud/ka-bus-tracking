package com.kabus.tracking.domain.repository;

import com.kabus.tracking.domain.entity.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    List<Favorite> findByDeviceIdOrderByCreatedAtDesc(String deviceId);

    Optional<Favorite> findFirstByDeviceIdAndItemTypeAndItemId(String deviceId, String itemType, Long itemId);

    void deleteByDeviceIdAndItemTypeAndItemId(String deviceId, String itemType, Long itemId);
}
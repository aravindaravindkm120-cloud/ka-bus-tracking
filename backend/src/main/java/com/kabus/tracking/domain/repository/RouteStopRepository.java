package com.kabus.tracking.domain.repository;

import com.kabus.tracking.domain.entity.RouteStop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface RouteStopRepository extends JpaRepository<RouteStop, Long> {
    List<RouteStop> findByRouteIdOrderByStopOrderAsc(Long routeId);

    default List<RouteStop> findByRouteIdOrderByStopOrder(Long routeId) {
        return findByRouteIdOrderByStopOrderAsc(routeId);
    }

    /** All ordered route-stops for a set of routes in one query (no N+1). */
    @Query("select rs from RouteStop rs where rs.route.id in :routeIds order by rs.route.id, rs.stopOrder")
    List<RouteStop> findByRouteIdIn(@Param("routeIds") Collection<Long> routeIds);

    void deleteByRouteId(Long routeId);

    long countByRouteId(Long routeId);

    @Query("select s.route.id, count(s) from RouteStop s where s.route.id in :routeIds group by s.route.id")
    List<Object[]> countByRouteIds(@Param("routeIds") Collection<Long> routeIds);

    /**
     * Bulk coordinate lookup for stop-suggestion names. {@code names} must be
     * pre-lowercased so the comparison stays case-insensitive.
     */
    @Query("select rs from RouteStop rs where rs.route.enabled = true "
            + "and rs.route.status = 'ACTIVE' and lower(rs.stopName) in :names")
    List<RouteStop> findCoordinatesForNames(@Param("names") Collection<String> names);
}

package com.kabus.tracking.domain.repository;

import com.kabus.tracking.domain.entity.Route;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RouteRepository extends JpaRepository<Route, Long> {

    List<Route> findByDivisionIdIn(List<Long> divisionIds);

    Optional<Route> findByCode(String code);

    List<Route> findByDivisionIdInAndStatus(List<Long> divisionIds, String status);

    long countByDivisionIdIn(List<Long> divisionIds);

    Page<Route> findByDivisionIdIn(Collection<Long> divisionIds, Pageable pageable);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);

    @Query("select r from Route r where r.division.id in :divisionIds and ("
            + "lower(r.code) like lower(concat('%', :term, '%')) "
            + "or lower(r.name) like lower(concat('%', :term, '%')) "
            + "or lower(r.origin) like lower(concat('%', :term, '%')) "
            + "or lower(r.destination) like lower(concat('%', :term, '%')))")
    Page<Route> searchInDivisions(@Param("divisionIds") Collection<Long> divisionIds,
                                  @Param("term") String term, Pageable pageable);

    @Query("select r from Route r where "
            + "lower(r.code) like lower(concat('%', :term, '%')) "
            + "or lower(r.name) like lower(concat('%', :term, '%')) "
            + "or lower(r.origin) like lower(concat('%', :term, '%')) "
            + "or lower(r.destination) like lower(concat('%', :term, '%'))")
    Page<Route> searchAll(@Param("term") String term, Pageable pageable);

    @Query("select r from Route r where r.enabled = true and r.status = 'ACTIVE' and "
            + "(lower(r.origin) like lower(concat('%', :term, '%')) "
            + "or lower(r.destination) like lower(concat('%', :term, '%')) "
            + "or lower(r.name) like lower(concat('%', :term, '%')) "
            + "or lower(r.code) like lower(concat('%', :term, '%'))) "
            + "order by r.name")
    List<Route> searchByTerm(@Param("term") String term, Pageable pageable);

    @Query("select distinct r from Route r where r.enabled = true and r.status = 'ACTIVE' and "
            + "((lower(r.origin) like lower(concat('%', :from, '%')) "
            + " or lower(r.destination) like lower(concat('%', :from, '%')) "
            + " or exists (select 1 from RouteStop s where s.route = r and lower(s.stopName) like lower(concat('%', :from, '%')))) "
            + " and (lower(r.origin) like lower(concat('%', :to, '%')) "
            + " or lower(r.destination) like lower(concat('%', :to, '%')) "
            + " or exists (select 1 from RouteStop s2 where s2.route = r and lower(s2.stopName) like lower(concat('%', :to, '%'))))) "
            + "order by r.name")
    List<Route> searchRouteBetweenStops(@Param("from") String from, @Param("to") String to, Pageable pageable);

    /** All routes eligible for passenger journeys (enabled and ACTIVE). */
    @Query("select r from Route r where r.enabled = true and r.status = 'ACTIVE' order by r.name")
    List<Route> findAllEnabledActive();

    // ------------------------------------------------------------------
    // Stop suggestions (authoritative stop search for the passenger FROM/TO
    // autocomplete). Sources are the real route_stops rows plus route
    // terminals, restricted to enabled ACTIVE routes. Matching is
    // case-insensitive; the caller pre-normalizes the term (trim, collapse
    // whitespace, NFKC) before passing it.
    // ------------------------------------------------------------------

    @Query("select distinct rs.stopName from RouteStop rs "
            + "where rs.route.enabled = true and rs.route.status = 'ACTIVE' "
            + "and lower(rs.stopName) like lower(concat('%', :term, '%')) "
            + "order by rs.stopName")
    List<String> searchStopNames(@Param("term") String term, Pageable pageable);

    @Query("select distinct r.origin from Route r where r.enabled = true and r.status = 'ACTIVE' "
            + "and lower(r.origin) like lower(concat('%', :term, '%')) order by r.origin")
    List<String> searchOriginNames(@Param("term") String term, Pageable pageable);

    @Query("select distinct r.destination from Route r where r.enabled = true and r.status = 'ACTIVE' "
            + "and lower(r.destination) like lower(concat('%', :term, '%')) order by r.destination")
    List<String> searchDestinationNames(@Param("term") String term, Pageable pageable);
}

package com.kabus.tracking.domain.repository;

import com.kabus.tracking.domain.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    long countByEnabledTrue();

    long countByEnabledFalse();

    Page<User> findAllByOrderByIdAsc(Pageable pageable);

    @Query("select u from User u where "
            + "lower(u.username) like lower(concat('%', :term, '%')) "
            + "or lower(u.fullName) like lower(concat('%', :term, '%')) "
            + "or lower(coalesce(u.email, '')) like lower(concat('%', :term, '%')) "
            + "order by u.id")
    Page<User> searchByTerm(@Param("term") String term, Pageable pageable);
}
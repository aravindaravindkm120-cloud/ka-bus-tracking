package com.kabus.tracking.domain.repository;

import com.kabus.tracking.domain.entity.Role;
import com.kabus.tracking.domain.enums.RoleCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByCode(RoleCode code);
}
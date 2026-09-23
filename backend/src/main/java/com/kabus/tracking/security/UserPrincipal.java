package com.kabus.tracking.security;

import com.kabus.tracking.domain.enums.RoleCode;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Authenticated principal. Role and scope originate solely from the signed
 * JWT issued by the backend, never from client-supplied data.
 */
@Getter
public class UserPrincipal implements UserDetails {

    private final Long userId;
    private final String username;
    private final String password;
    private final boolean enabled;
    private final boolean locked;
    private final Set<RoleCode> roles;
    private final Long corporationId;
    private final Long divisionId;
    private final Long depotId;
    private final Long townId;

    public UserPrincipal(Long userId, String username, String password,
                         boolean enabled, boolean locked, Set<RoleCode> roles,
                         Long corporationId, Long divisionId, Long depotId, Long townId) {
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.enabled = enabled;
        this.locked = locked;
        this.roles = roles == null ? Set.of() : roles;
        this.corporationId = corporationId;
        this.divisionId = divisionId;
        this.depotId = depotId;
        this.townId = townId;
    }

    public boolean hasRole(RoleCode role) {
        return roles.contains(role);
    }

    public boolean isSuperAdmin() {
        return roles.contains(RoleCode.SUPER_ADMIN);
    }

    public boolean isCrew() {
        return roles.contains(RoleCode.DRIVER) || roles.contains(RoleCode.CONDUCTOR);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r.name()))
                .collect(Collectors.toSet());
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !locked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
}
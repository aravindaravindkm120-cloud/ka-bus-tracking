package com.kabus.tracking.security;

import com.kabus.tracking.domain.entity.User;
import com.kabus.tracking.domain.enums.RoleCode;
import com.kabus.tracking.domain.repository.UserRepository;
import com.kabus.tracking.service.RoleMembershipService;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * JWT bearer authentication.
 *
 * <p>The token carries the <b>single role</b> and its server-side scope that
 * were bound at login. On every request this filter (a) verifies the signature,
 * (b) reloads the account to check existence/enabled/locked, and (c) re-validates
 * the role membership against the role profile / crew tables. No authority is
 * ever taken from client-supplied data or from a generic role column: the
 * authorities are derived from the token's authenticated role and only granted
 * while the membership row still exists.</p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final RoleMembershipService roleMembershipService;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   UserRepository userRepository,
                                   RoleMembershipService roleMembershipService) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.roleMembershipService = roleMembershipService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ") || SecurityContextHolder.getContext().getAuthentication() != null) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);
        try {
            Claims claims = jwtService.parse(token);
            java.util.Optional<RoleCode> sessionRole = JwtService.sessionRole(claims);
            if (sessionRole.isEmpty()) {
                chain.doFilter(request, response);
                return;
            }
            RoleCode role = sessionRole.get();

            String username = claims.getSubject();
            User user = username == null ? null : userRepository.findByUsername(username).orElse(null);
            if (user == null || !user.isEnabled() || user.isLocked()) {
                chain.doFilter(request, response);
                return;
            }

            // Membership must still exist in the role profile / crew table.
            if (roleMembershipService.resolve(role, user.getId()).isEmpty()) {
                chain.doFilter(request, response);
                return;
            }

            Number uid = claims.get(JwtService.CLAIM_USER_ID, Number.class);
            Long corporationId = claims.containsKey(JwtService.CLAIM_CORPORATION)
                    ? toLong(claims.get(JwtService.CLAIM_CORPORATION)) : null;
            Long divisionId = claims.containsKey(JwtService.CLAIM_DIVISION)
                    ? toLong(claims.get(JwtService.CLAIM_DIVISION)) : null;
            Long depotId = claims.containsKey(JwtService.CLAIM_DEPOT)
                    ? toLong(claims.get(JwtService.CLAIM_DEPOT)) : null;
            Long townId = claims.containsKey(JwtService.CLAIM_TOWN)
                    ? toLong(claims.get(JwtService.CLAIM_TOWN)) : null;

            UserPrincipal principal = new UserPrincipal(
                    uid == null ? user.getId() : uid.longValue(),
                    user.getUsername(),
                    "",
                    user.isEnabled(),
                    user.isLocked(),
                    Set.of(role),
                    corporationId,
                    divisionId,
                    depotId,
                    townId);

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Rejected JWT: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("Authentication lookup failed: {}", e.getMessage());
        }

        chain.doFilter(request, response);
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
package com.kabus.tracking.service;

import com.kabus.tracking.config.AppProperties;
import com.kabus.tracking.domain.entity.RefreshToken;
import com.kabus.tracking.domain.entity.User;
import com.kabus.tracking.domain.enums.RoleCode;
import com.kabus.tracking.domain.repository.RefreshTokenRepository;
import com.kabus.tracking.domain.repository.UserRepository;
import com.kabus.tracking.exception.ApiException;
import com.kabus.tracking.security.JwtService;
import com.kabus.tracking.security.UserPrincipal;
import com.kabus.tracking.web.dto.AuthDtos;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;

/**
 * Authentication with strict, role-specific login.
 *
 * <p><b>Crew</b> accounts authenticate at {@code POST /api/auth/login}: the
 * JWT carries exactly the DRIVER/CONDUCTOR role bound to the linked crew
 * record. <b>Admin</b> accounts authenticate at
 * {@code POST /api/auth/admin/login} by email + password + the role they want
 * to assume. The backend verifies the requested role against the role profile
 * tables (super_admins / division_admins / ... / town_managers). Valid
 * credentials with no membership in the requested role table return 403;
 * invalid credentials return 401. One JWT always represents exactly one role
 * and its server-side scope.</p>
 */
@Service
public class AuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RoleMembershipService roleMembershipService;
    private final AppProperties props;

    public AuthService(AuthenticationManager authenticationManager,
                       JwtService jwtService,
                       UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       RoleMembershipService roleMembershipService,
                       AppProperties props) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.roleMembershipService = roleMembershipService;
        this.props = props;
    }

    /**
     * Crew login (DRIVER/CONDUCTOR). The session role is derived exclusively
     * from the linked crew record; an account without one is rejected with
     * 403 even when the password is correct.
     */
    @Transactional
    public AuthDtos.LoginResponse crewLogin(AuthDtos.LoginRequest request) {
        User user = authenticate(request.username(), request.password());

        RoleCode crewRole = roleMembershipService.crewRole(user.getId())
                .orElseThrow(() -> ApiException.forbidden(
                        "This account is not a crew member (DRIVER/CONDUCTOR). Log in with the crew app role you were assigned."));

        RoleMembershipService.ScopeRef scope = new RoleMembershipService.ScopeRef(
                null,
                user.getDivision() == null ? null : user.getDivision().getId(),
                user.getDepot() == null ? null : user.getDepot().getId(),
                user.getTown() == null ? null : user.getTown().getId());

        return buildLoginResponse(user, crewRole, scope);
    }

    /**
     * Admin login: email + password + the single role being requested.
     * 401 for invalid credentials, 403 for valid credentials whose account is
     * not a member of the requested role.
     */
    @Transactional
    public AuthDtos.LoginResponse adminLogin(AuthDtos.AdminLoginRequest request) {
        RoleCode requestedRole;
        try {
            requestedRole = RoleCode.valueOf(request.requestedRole().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Unsupported role: " + request.requestedRole());
        }
        if (!roleMembershipService.isAdminRole(requestedRole)) {
            throw ApiException.badRequest("Unsupported role: " + requestedRole.name()
                    + ". Use the admin login with an admin role.");
        }

        String email = request.email().trim().toLowerCase();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> ApiException.unauthorized("Invalid email or password."));

        authenticate(user.getUsername(), request.password());

        Optional<RoleMembershipService.ScopeRef> scope = roleMembershipService.resolve(requestedRole, user.getId());
        if (scope.isEmpty()) {
            throw ApiException.forbidden("Account does not hold the role " + requestedRole.name()
                    + ". Verify the role in your admin app and sign in with your assigned role.");
        }

        return buildLoginResponse(user, requestedRole, scope.get());
    }

    @Transactional
    public AuthDtos.RefreshResponse refresh(String refreshToken) {
        String hash = sha256Hex(refreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHashAndRevokedFalse(hash)
                .orElseThrow(() -> ApiException.unauthorized("Invalid or expired refresh token."));
        if (!stored.isRevoked() && stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            stored.setRevoked(true);
            refreshTokenRepository.save(stored);
            throw ApiException.unauthorized("Refresh token expired. Please log in again.");
        }

        String sessionRoleName = stored.getAuthenticatedRole();
        RoleCode sessionRole;
        try {
            sessionRole = RoleCode.valueOf(sessionRoleName);
        } catch (IllegalArgumentException e) {
            throw ApiException.unauthorized("Session role lost. Please log in again.");
        }

        User user = stored.getUser();
        Optional<RoleMembershipService.ScopeRef> scope = roleMembershipService.resolve(sessionRole, user.getId());
        if (scope.isEmpty()) {
            stored.setRevoked(true);
            refreshTokenRepository.save(stored);
            throw ApiException.forbidden("Role membership was revoked. Please log in again.");
        }

        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        String newRefresh = issueRefreshToken(user, sessionRole);
        String newAccess = accessToken(user, sessionRole, scope.get());
        return new AuthDtos.RefreshResponse(newAccess, newRefresh, "Bearer",
                props.getJwt().getExpirationMs() / 1000);
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHashAndRevokedFalse(sha256Hex(refreshToken))
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });
    }

    @Transactional(readOnly = true)
    public AuthDtos.LoginResponse currentSession(UserPrincipal principal) {
        User user = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> ApiException.unauthorized("Account not found."));
        RoleCode sessionRole = principal.getRoles().stream().findFirst()
                .orElseThrow(() -> ApiException.unauthorized("No role on this session."));
        return buildLoginResponse(user, sessionRole, principalScope(principal, sessionRole));
    }

    private RoleMembershipService.ScopeRef principalScope(UserPrincipal principal, RoleCode sessionRole) {
        // The principal's scope mirrors the signed-token claims issued at login.
        return new RoleMembershipService.ScopeRef(
                principal.getCorporationId(), principal.getDivisionId(), principal.getDepotId(), principal.getTownId());
    }

    private AuthDtos.LoginResponse buildLoginResponse(User user, RoleCode role,
                                                      RoleMembershipService.ScopeRef scope) {
        return new AuthDtos.LoginResponse(
                new AuthDtos.AuthUser(
                        user.getId(),
                        user.getUsername(),
                        user.getFullName(),
                        user.getEmail(),
                        user.getPhone(),
                        Set.of(role),
                        scope.divisionId(),
                        scope.depotId(),
                        scope.townId()),
                accessToken(user, role, scope),
                issueRefreshToken(user, role),
                "Bearer",
                props.getJwt().getExpirationMs() / 1000);
    }

    private String accessToken(User user, RoleCode role, RoleMembershipService.ScopeRef scope) {
        return jwtService.generateAccessToken(role, user.getId(), user.getUsername(), scope);
    }

    private String issueRefreshToken(User user, RoleCode role) {
        byte[] bytes = new byte[48];
        SECURE_RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        RefreshToken entity = new RefreshToken();
        entity.setUser(user);
        entity.setAuthenticatedRole(role.name());
        entity.setTokenHash(sha256Hex(token));
        entity.setExpiresAt(LocalDateTime.now().plusSeconds(props.getJwt().getRefreshExpirationMs() / 1000));
        refreshTokenRepository.save(entity);
        return token;
    }

    private User authenticate(String username, String password) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password));
        } catch (BadCredentialsException | org.springframework.security.core.userdetails.UsernameNotFoundException e) {
            throw ApiException.unauthorized("Invalid username or password.");
        }
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return userRepository.findById(principal.getUserId())
                .orElseThrow(() -> ApiException.unauthorized("Account not found."));
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
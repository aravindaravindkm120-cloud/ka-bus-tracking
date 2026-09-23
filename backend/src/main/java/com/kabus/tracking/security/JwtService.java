package com.kabus.tracking.security;

import com.kabus.tracking.config.AppProperties;
import com.kabus.tracking.domain.enums.RoleCode;
import com.kabus.tracking.service.RoleMembershipService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Issues and validates access JWTs.
 *
 * <p>Each token authenticates the identity <i>and</i> the single role it was
 * issued for, together with the organizational scope bound to that role
 * membership. The token is produced only after the backend verified the role
 * membership against the role profile / crew tables. Authorization decisions
 * use the role embedded in the signed token (never client input) and the
 * membership is re-validated against the database on every request.</p>
 */
@Service
public class JwtService {

    public static final String CLAIM_USER_ID = "uid";
    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_SESSION_ROLE = "session_role";
    public static final String CLAIM_CORPORATION = "corp";
    public static final String CLAIM_DIVISION = "div";
    public static final String CLAIM_DEPOT = "dep";
    public static final String CLAIM_TOWN = "town";

    private final AppProperties props;
    private final SecretKey key;

    public JwtService(AppProperties props) {
        this.props = props;
        String secret = props.getJwt().getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SECRET environment variable is missing. Refusing to start with an empty JWT secret.");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET must be at least 32 bytes long (HS256 requirement). Got "
                            + secret.getBytes(StandardCharsets.UTF_8).length + " bytes.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Issues an access token for a specific, already-verified role membership.
     * The token carries exactly one role and its server-side scope.
     */
    public String generateAccessToken(RoleCode role, Long userId, String username,
                                      RoleMembershipService.ScopeRef scope) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + props.getJwt().getExpirationMs());
        return Jwts.builder()
                .subject(username)
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_ROLES, List.of(role.name()))
                .claim(CLAIM_SESSION_ROLE, role.name())
                .claim(CLAIM_CORPORATION, scope == null ? null : scope.corporationId())
                .claim(CLAIM_DIVISION, scope == null ? null : scope.divisionId())
                .claim(CLAIM_DEPOT, scope == null ? null : scope.depotId())
                .claim(CLAIM_TOWN, scope == null ? null : scope.townId())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    /**
     * Parses and verifies the token. Throws a JWT exception when invalid/expired.
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUsername(String token) {
        return parse(token).getSubject();
    }

    @SuppressWarnings("unused")
    public long getExpirationMs() {
        return props.getJwt().getExpirationMs();
    }

    public static Set<RoleCode> rolesFrom(Claims claims) {
        List<?> raw = claims.get(CLAIM_ROLES, List.class);
        if (raw == null) {
            return Set.of();
        }
        return raw.stream()
                .map(Object::toString)
                .filter(code -> RoleCode.isSupported(code))
                .map(RoleCode::valueOf)
                .collect(Collectors.toSet());
    }

    /** The single role this token was issued for, or empty if absent/unsupported. */
    public static java.util.Optional<RoleCode> sessionRole(Claims claims) {
        if (claims == null) {
            return java.util.Optional.empty();
        }
        Object raw = claims.get(CLAIM_SESSION_ROLE);
        if (raw == null || !RoleCode.isSupported(raw.toString())) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(RoleCode.valueOf(raw.toString()));
    }
}
# Security Audit — KA Bus Tracking

Date: 2026-09-21 — **PHASE 11**. Identifies what is already enforced and what
remains a deployment-time responsibility. No issues requiring code changes were
found during the management-API expansion (Phases 3–10); the notes below are the
controls that were *added or re-verified* during that work.

## 1. Authentication

| Control | Where | Status |
|---|---|---|
| BCrypt password hashing (cost 10) | `AuthService` / `PasswordEncoder` bean | ✅ |
| JWT access tokens (HS256, jjwt 0.12.6), refresh rotation | `AuthService`, `RefreshToken` | ✅ |
| Refresh tokens stored as SHA-256 hash, single-use, revocation on role change / disable / password reset | `RefreshTokenRepository.revokeAllForUser` (+ `AdminUserService`) | ✅ NEW |
| Strict role login (`/api/auth/admin/login` must prove membership in the requested role table) | `AuthService` + `StrictRoleLoginTest` | ✅ |
| Account disablement invalidates tokens immediately | `JwtAuthenticationFilter` re-checks `enabled` + `locked` (and role membership) on every request | ✅ |
| AES-GCM encrypted session storage on device | Android `Session` (EncryptedSharedPreferences) | ✅ |

## 2. Authorization (strict, server-side, scope-aware)

- Route-level gating: `ADMIN_ROLES` + role-specific endpoints (`SecurityConfig`).
- **Service-level guards (defence in depth)** — a caller can never exceed scope
  even with a hand-crafted token:
  - `ScopeResolver` derives division/depot/town ids from the *profile tables*
    (never from claims).
  - `ScopeGuard.requireWithin` enforces mutation scope:
    `DIVISION_ADMIN/MANAGER` → own division; `DEPOT_HEAD` → own depot;
    `TOWN_MANAGER` → own town. **NEW** in Phases 4–7 (fleet, staff, trips);
    route writes are division-level so depot/town roles receive 403.
  - SUPER_ADMIN-only services (`AdminUserService`, `AuditLogService`,
    `SettingsAdminService`, `OrgManagementService`) reject others with 403 at
    the service layer, independent of path-level security.
- No `@PreAuthorize` string leagues; enforcement is role-object + scope math,
  unit-tested per role in `AuthorizationScopeTest`.

## 3. OWASP Top 10 mapping

| OWASP | Status | Evidence |
|---|---|---|
| A01 Broken Access Control | ✅ mitigated | Service-level `ScopeGuard`, SUPER_ADMIN guards, `AuthorizationScopeTest`, `OperationsManagementTest` |
| A02 Cryptographic Failures | ✅ | BCrypt at rest; HS256 tokens; TLS is a deployment obligation (Appache/nginx/cloud LB) |
| A03 Injection | ✅ | JPA/Spring Data (parameterised), no string-built SQL |
| A04 Insecure Design | ✅ | Strict role model; scope never client-provided |
| A05 Security Misconfiguration | ⚠️ ops | `allowedOrigins` must be locked to the prod domains; `open-in-view:false` (test parity) |
| A06 Vulnerable Components | ⚠️ ops | Regular dependency bumps; SBOM via `mvn dependency:tree` |
| A07 Auth failures | ✅ | No user enumeration (consistent 401 message), rate-limited login (5/min/IP+user) |
| A08 Integrity | ✅ | Refresh-token rotation; no forged writes without JWT |
| A09 Logging & Monitoring | ✅ | `AuditLog` rows on every mutation (Phases 3–10) + existing request logging |
| A10 SSRF | ✅ | Outbound OSRM calls only to the configured `app.routing.url`; config-driven |

## 4. Audit trail (added across Phases 3–10)

- Every successful mutation records an `audit_logs` row via `AuditService`
  (`record(Long actorUserId, action, resourceType, resourceId, detail, ip, ua)`),
  including org, user, fleet, staff, route (+ geometry), trip, settings.
- Actor id is the real principal (`UserPrincipal.getUserId()`); system actions
  carry a null actor.
- Viewer: `GET /api/admin/audit-logs` (SUPER_ADMIN), paged + filterable.
- History preserved: disable-instead-of-delete for users, buses, routes
  (FK + GPS/trip history integrity).

## 5. Secrets & data hygiene

- No secret is logged; password hash fields are never serialized
  (`@JsonIgnore` + test assertion `no password hash in response`).
- Device tokens, ad-impression and GPS payloads carry no PII beyond what is
  required; DB backups must be encrypted at the platform layer.

## 6. Remaining deployment responsibilities (not code)

1. TLS everywhere + HSTS; force `HTTPS` in the reverse proxy.
2. `spring.datasource.username/password`, JWT secret, `app.routing.url`
   via env/secrets manager — never in a committed properties file.
3. CORS `allowed-origins` = exact prod domains (passenger-web CDN, admin app).
4. Keep agency-less: MySQL accounts least-privilege; separate backups account.
5. Regular dependency updates (A06) and a scheduled `mvn dependency:tree` SBOM.

**Conclusion:** no code-level security defects found in this pass. The expansion
from org-only management to the full admin surface (users, fleet, staff, routes,
trips, audit, settings) preserved the strict server-side scope model and added
revocation + AuditLog coverage. PHASE 11 complete.
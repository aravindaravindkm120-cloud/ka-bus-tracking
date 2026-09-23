package com.kabus.tracking.service;

/**
 * Organizational scope for an authenticated admin user.
 *
 * <p>Derived server-side from the user's role and the organizational node the
 * user is attached to. Never constructed from client input.</p>
 */
public record AccessScope(
        boolean wholeSystem,
        Long corporationId,
        Long divisionId,
        Long depotId,
        Long townId
) {

    public static AccessScope system() {
        return new AccessScope(true, null, null, null, null);
    }

    /** A corporation-wide admin scope (a corporation DIVISION_ADMIN). */
    public static AccessScope corporation(Long corporationId) {
        return new AccessScope(false, corporationId, null, null, null);
    }

    public static AccessScope division(Long divisionId) {
        return new AccessScope(false, null, divisionId, null, null);
    }

    public static AccessScope depot(Long divisionId, Long depotId) {
        return new AccessScope(false, null, divisionId, depotId, null);
    }

    public static AccessScope town(Long divisionId, Long depotId, Long townId) {
        return new AccessScope(false, null, divisionId, depotId, townId);
    }
}
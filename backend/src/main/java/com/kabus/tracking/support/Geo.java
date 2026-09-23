package com.kabus.tracking.support;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Geo {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private Geo() {
    }

    /** Great-circle distance between two points in kilometers (haversine). */
    public static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    public static double distanceKm(BigDecimal lat1, BigDecimal lon1, BigDecimal lat2, BigDecimal lon2) {
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) {
            return Double.POSITIVE_INFINITY;
        }
        return distanceKm(lat1.doubleValue(), lon1.doubleValue(), lat2.doubleValue(), lon2.doubleValue());
    }

    /** Approximate degrees of latitude for a given radius in km (for bounding boxes). */
    public static double latDeltaKm(double radiusKm) {
        return radiusKm / 111.0;
    }

    /** Approximate degrees of longitude for a given radius in km at a given latitude. */
    public static double lonDeltaKm(double radiusKm, double latitude) {
        double cos = Math.cos(Math.toRadians(latitude));
        return cos == 0 ? 180.0 : radiusKm / (111.0 * cos);
    }

    public static BigDecimal round(BigDecimal value, int scale) {
        return value == null ? null : value.setScale(scale, RoundingMode.HALF_UP);
    }
}
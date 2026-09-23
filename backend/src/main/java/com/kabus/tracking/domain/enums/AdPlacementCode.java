package com.kabus.tracking.domain.enums;

import java.util.Arrays;

public enum AdPlacementCode {
    PASSENGER_WEB_HOME,
    PASSENGER_WEB_SEARCH,
    PASSENGER_WEB_BUS_DETAILS,
    PASSENGER_WEB_MAP_BOTTOM,
    ADMIN_APP_DASHBOARD,
    ADMIN_APP_NON_CRITICAL,
    CREW_APP_HOME,
    CREW_APP_NON_CRITICAL;

    public static boolean isSupported(String code) {
        return Arrays.stream(values()).anyMatch(v -> v.name().equalsIgnoreCase(code));
    }
}
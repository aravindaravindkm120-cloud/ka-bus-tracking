package com.kabus.tracking.domain.enums;

import java.util.Arrays;

public enum RoleCode {
    SUPER_ADMIN,
    DIVISION_ADMIN,
    DIVISION_MANAGER,
    DEPOT_HEAD,
    TOWN_MANAGER,
    DRIVER,
    CONDUCTOR;

    public static boolean isSupported(String code) {
        if (code == null) {
            return false;
        }
        return Arrays.stream(values()).anyMatch(v -> v.name().equalsIgnoreCase(code));
    }
}
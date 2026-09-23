package com.kabus.tracking;

import com.kabus.tracking.domain.entity.Division;
import com.kabus.tracking.domain.entity.User;
import com.kabus.tracking.domain.enums.RoleCode;

/**
 * DIVISION_MANAGER isolation. A division manager has the same division-level
 * read/write scope as a division admin (see {@link ScopeResolver}), so it must
 * pass the same cross-division and SUPER_ADMIN-only checks.
 */
class DivisionManagerScopeTest extends DivisionScopedAdminScopeTest {

    @Override
    protected RoleCode role() {
        return RoleCode.DIVISION_MANAGER;
    }

    @Override
    protected void assignProfile(User user, Division division) {
        divisionManagerProfile(user, division);
    }
}

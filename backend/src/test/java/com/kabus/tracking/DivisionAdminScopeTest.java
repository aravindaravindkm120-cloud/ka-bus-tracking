package com.kabus.tracking;

import com.kabus.tracking.domain.entity.Division;
import com.kabus.tracking.domain.entity.User;
import com.kabus.tracking.domain.enums.RoleCode;

/** DIVISION_ADMIN isolation, see {@link DivisionScopedAdminScopeTest}. */
class DivisionAdminScopeTest extends DivisionScopedAdminScopeTest {

    @Override
    protected RoleCode role() {
        return RoleCode.DIVISION_ADMIN;
    }

    @Override
    protected void assignProfile(User user, Division division) {
        divisionAdminProfile(user, division);
    }
}

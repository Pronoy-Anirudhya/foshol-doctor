package com.rootcause.foshol.intake.application.query;

import com.rootcause.foshol.common.enums.Role;
import com.rootcause.foshol.identity.api.OfficerLookupApi;
import com.rootcause.foshol.intake.application.port.CaseQueryPort;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class StaffRegionAccess {

    private final CaseQueryPort queries;
    private final OfficerLookupApi officers;

    public StaffRegionAccess(CaseQueryPort queries, OfficerLookupApi officers) {
        this.queries = queries;
        this.officers = officers;
    }

    public boolean allows(Role role, UUID callerId, UUID caseId) {
        if (role != Role.OFFICER && role != Role.ADMIN) {
            return false;
        }
        String district = queries.findDistrictCode(caseId).orElse(null);
        if (district == null || callerId == null) {
            return false;
        }
        return officers.findById(callerId).map(o -> district.equals(o.districtCode())).orElse(false);
    }
}

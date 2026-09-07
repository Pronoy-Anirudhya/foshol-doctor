package com.rootcause.foshol.identity.application.query;

import com.rootcause.foshol.common.ErrorCodes;
import com.rootcause.foshol.common.Role;
import com.rootcause.foshol.identity.domain.IdentityException;
import com.rootcause.foshol.identity.infrastructure.FarmerJpaRepository;
import com.rootcause.foshol.identity.infrastructure.FarmerReadRow;
import com.rootcause.foshol.identity.infrastructure.FieldOfficerJpaRepository;
import com.rootcause.foshol.identity.infrastructure.OfficerReadRow;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MeQueryHandler {

    private final FarmerJpaRepository farmers;
    private final FieldOfficerJpaRepository officers;

    public MeQueryHandler(FarmerJpaRepository farmers, FieldOfficerJpaRepository officers) {
        this.farmers = farmers;
        this.officers = officers;
    }

    @Transactional(readOnly = true)
    public MeView handle(MeQuery query) {
        UUID id = query.subjectId();
        Role role = Role.valueOf(query.role());
        if (role == Role.FARMER) {
            FarmerReadRow farmer = farmers.findReadById(id)
                    .orElseThrow(() -> new IdentityException(ErrorCodes.ERR_SUBJECT_NOT_FOUND, 404, "Subject not found."));
            return new MeView(
                    farmer.getId(),
                    Role.FARMER.name(),
                    farmer.getName(),
                    farmer.getDistrictCode(),
                    farmer.getPreferredLanguage(),
                    null);
        }
        OfficerReadRow officer = officers.findReadById(id)
                .orElseThrow(() -> new IdentityException(ErrorCodes.ERR_SUBJECT_NOT_FOUND, 404, "Subject not found."));
        return new MeView(
                officer.getId(),
                officer.getRole(),
                officer.getName(),
                officer.getDistrictCode(),
                null,
                officer.getUsername());
    }
}

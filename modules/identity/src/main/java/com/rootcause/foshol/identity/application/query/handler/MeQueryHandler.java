package com.rootcause.foshol.identity.application.query.handler;

import com.rootcause.foshol.common.contract.ErrorCodes;
import com.rootcause.foshol.common.enums.Role;
import com.rootcause.foshol.common.cqrs.QueryHandler;
import com.rootcause.foshol.identity.application.port.FarmerReadPort;
import com.rootcause.foshol.identity.application.port.FarmerReadSnapshot;
import com.rootcause.foshol.identity.application.port.GeoLabelPort;
import com.rootcause.foshol.identity.application.port.GeoLabels;
import com.rootcause.foshol.identity.application.port.OfficerReadPort;
import com.rootcause.foshol.identity.application.port.OfficerReadSnapshot;
import com.rootcause.foshol.identity.application.query.MeQuery;
import com.rootcause.foshol.identity.application.query.MeView;
import com.rootcause.foshol.identity.domain.IdentityException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MeQueryHandler implements QueryHandler<MeQuery, MeView> {

    @Override
    public Class<MeQuery> queryType() {
        return MeQuery.class;
    }

    private final FarmerReadPort farmers;
    private final OfficerReadPort officers;
    private final GeoLabelPort geoLabels;

    public MeQueryHandler(FarmerReadPort farmers, OfficerReadPort officers, GeoLabelPort geoLabels) {
        this.farmers = farmers;
        this.officers = officers;
        this.geoLabels = geoLabels;
    }

    @Transactional(readOnly = true)
    @Override
    public MeView handle(MeQuery query) {
        UUID id = query.subjectId();
        Role role = Role.valueOf(query.role());
        if (role == Role.FARMER) {
            FarmerReadSnapshot farmer = farmers.findReadById(id)
                    .orElseThrow(() -> new IdentityException(ErrorCodes.ERR_SUBJECT_NOT_FOUND, 404, "Subject not found."));
            GeoLabels geo = geoLabels.forDistrict(farmer.districtCode(), farmer.divisionCode());
            return new MeView(
                    farmer.id(),
                    Role.FARMER.name(),
                    farmer.name(),
                    farmer.districtCode(),
                    farmer.divisionCode(),
                    geo.districtNameBn(),
                    geo.districtNameEn(),
                    geo.divisionNameBn(),
                    geo.divisionNameEn(),
                    farmer.preferredLanguage(),
                    null);
        }
        OfficerReadSnapshot officer = officers.findReadById(id)
                .orElseThrow(() -> new IdentityException(ErrorCodes.ERR_SUBJECT_NOT_FOUND, 404, "Subject not found."));
        GeoLabels geo = geoLabels.forDistrict(officer.districtCode(), officer.divisionCode());
        return new MeView(
                officer.id(),
                officer.role(),
                officer.name(),
                officer.districtCode(),
                officer.divisionCode(),
                geo.districtNameBn(),
                geo.districtNameEn(),
                geo.divisionNameBn(),
                geo.divisionNameEn(),
                null,
                officer.username());
    }
}

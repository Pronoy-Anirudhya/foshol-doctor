package com.rootcause.foshol.identity.application;

import com.rootcause.foshol.identity.infrastructure.FarmerDirectoryRow;
import com.rootcause.foshol.identity.infrastructure.FarmerEntity;
import com.rootcause.foshol.identity.infrastructure.FieldOfficerJpaRepository;
import com.rootcause.foshol.identity.infrastructure.GeoLabelLookup;
import com.rootcause.foshol.identity.infrastructure.OfficerReadRow;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class FarmerRecordAssembler {

    private final GeoLabelLookup geoLabels;
    private final FieldOfficerJpaRepository officers;

    public FarmerRecordAssembler(GeoLabelLookup geoLabels, FieldOfficerJpaRepository officers) {
        this.geoLabels = geoLabels;
        this.officers = officers;
    }

    public FarmerRecord assemble(FarmerEntity farmer) {
        return assemble(
                farmer.getId(),
                farmer.getName(),
                farmer.getDivisionCode(),
                farmer.getDistrictCode(),
                farmer.getPreferredLanguage(),
                farmer.getCreatedAt(),
                farmer.getRegisteredBy(),
                farmer.getRegistrationSource());
    }

    public FarmerRecord assemble(FarmerDirectoryRow farmer) {
        return assemble(
                farmer.getId(),
                farmer.getName(),
                farmer.getDivisionCode(),
                farmer.getDistrictCode(),
                farmer.getPreferredLanguage(),
                farmer.getCreatedAt(),
                farmer.getRegisteredBy(),
                farmer.getRegistrationSource());
    }

    private FarmerRecord assemble(
            UUID id,
            String name,
            String divisionCode,
            String districtCode,
            String preferredLanguage,
            Instant createdAt,
            UUID registeredBy,
            String source) {
        GeoLabelLookup.Labels geo = geoLabels.forDistrict(districtCode, divisionCode);
        String officerName = null;
        if (registeredBy != null) {
            officerName = officers.findReadById(registeredBy).map(OfficerReadRow::getName).orElse(null);
        }
        return new FarmerRecord(
                id,
                name,
                divisionCode,
                districtCode,
                geo.divisionNameBn(),
                geo.divisionNameEn(),
                geo.districtNameBn(),
                geo.districtNameEn(),
                preferredLanguage,
                createdAt,
                registeredBy,
                officerName,
                source);
    }
}

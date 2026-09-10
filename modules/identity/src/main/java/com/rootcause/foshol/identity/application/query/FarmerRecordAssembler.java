package com.rootcause.foshol.identity.application.query;

import com.rootcause.foshol.identity.application.port.FarmerDirectorySnapshot;
import com.rootcause.foshol.identity.application.port.FarmerSnapshot;
import com.rootcause.foshol.identity.application.port.GeoLabelPort;
import com.rootcause.foshol.identity.application.port.GeoLabels;
import com.rootcause.foshol.identity.application.port.OfficerReadPort;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class FarmerRecordAssembler {

    private final GeoLabelPort geoLabels;
    private final OfficerReadPort officers;

    public FarmerRecordAssembler(GeoLabelPort geoLabels, OfficerReadPort officers) {
        this.geoLabels = geoLabels;
        this.officers = officers;
    }

    public FarmerRecord assemble(FarmerSnapshot farmer) {
        return assemble(
                farmer.id(),
                farmer.name(),
                farmer.divisionCode(),
                farmer.districtCode(),
                farmer.preferredLanguage(),
                farmer.createdAt(),
                farmer.registeredBy(),
                farmer.registrationSource());
    }

    public FarmerRecord assemble(FarmerDirectorySnapshot farmer) {
        return assemble(
                farmer.id(),
                farmer.name(),
                farmer.divisionCode(),
                farmer.districtCode(),
                farmer.preferredLanguage(),
                farmer.createdAt(),
                farmer.registeredBy(),
                farmer.registrationSource());
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
        GeoLabels geo = geoLabels.forDistrict(districtCode, divisionCode);
        String officerName = null;
        if (registeredBy != null) {
            officerName = officers.findReadById(registeredBy).map(row -> row.name()).orElse(null);
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

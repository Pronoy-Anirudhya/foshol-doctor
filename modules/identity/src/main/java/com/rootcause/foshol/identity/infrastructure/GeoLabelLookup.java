package com.rootcause.foshol.identity.infrastructure;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GeoLabelLookup {

    public record Labels(
            String divisionCode,
            String districtCode,
            String divisionNameEn,
            String divisionNameBn,
            String districtNameEn,
            String districtNameBn) {}

    private final GeoDistrictJpaRepository districts;

    public GeoLabelLookup(GeoDistrictJpaRepository districts) {
        this.districts = districts;
    }

    @Transactional(readOnly = true)
    public Labels forDistrict(String districtCode, String divisionCodeFallback) {
        return districts
                .findById(districtCode)
                .map(d -> new Labels(
                        d.getDivision().getCode(),
                        d.getCode(),
                        d.getDivision().getNameEn(),
                        d.getDivision().getNameBn(),
                        d.getNameEn(),
                        d.getNameBn()))
                .orElse(new Labels(divisionCodeFallback, districtCode, null, null, null, null));
    }
}

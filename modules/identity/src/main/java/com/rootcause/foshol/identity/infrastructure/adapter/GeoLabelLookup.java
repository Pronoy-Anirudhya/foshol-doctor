package com.rootcause.foshol.identity.infrastructure.adapter;

import com.rootcause.foshol.identity.application.port.GeoLabelPort;
import com.rootcause.foshol.identity.application.port.GeoLabels;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.rootcause.foshol.identity.infrastructure.persistence.GeoDistrictJpaRepository;

@Service
public class GeoLabelLookup implements GeoLabelPort {

    private final GeoDistrictJpaRepository districts;

    public GeoLabelLookup(GeoDistrictJpaRepository districts) {
        this.districts = districts;
    }

    @Override
    @Transactional(readOnly = true)
    public GeoLabels forDistrict(String districtCode, String divisionCodeFallback) {
        return districts
                .findById(districtCode)
                .map(d -> new GeoLabels(
                        d.getDivision().getCode(),
                        d.getCode(),
                        d.getDivision().getNameEn(),
                        d.getDivision().getNameBn(),
                        d.getNameEn(),
                        d.getNameBn()))
                .orElse(new GeoLabels(divisionCodeFallback, districtCode, null, null, null, null));
    }
}

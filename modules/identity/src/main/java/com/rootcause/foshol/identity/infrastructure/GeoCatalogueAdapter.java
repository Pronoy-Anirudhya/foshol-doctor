package com.rootcause.foshol.identity.infrastructure;

import com.rootcause.foshol.identity.application.query.DistrictResponse;
import com.rootcause.foshol.identity.application.query.DivisionResponse;
import com.rootcause.foshol.identity.application.query.GeoCataloguePort;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class GeoCatalogueAdapter implements GeoCataloguePort {

    private final GeoDivisionJpaRepository divisions;
    private final GeoDistrictJpaRepository districts;

    public GeoCatalogueAdapter(GeoDivisionJpaRepository divisions, GeoDistrictJpaRepository districts) {
        this.divisions = divisions;
        this.districts = districts;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DivisionResponse> listDivisions() {
        return divisions.findAllByOrderByNameEnAsc().stream()
                .map(d -> new DivisionResponse(d.getCode(), d.getNameEn(), d.getNameBn()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<List<DistrictResponse>> listDistricts(String divisionCode) {
        if (divisions.findById(divisionCode).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(districts.findByDivision_CodeOrderByNameEnAsc(divisionCode).stream()
                .map(d -> new DistrictResponse(
                        d.getCode(), d.getDivision().getCode(), d.getNameEn(), d.getNameBn()))
                .toList());
    }
}

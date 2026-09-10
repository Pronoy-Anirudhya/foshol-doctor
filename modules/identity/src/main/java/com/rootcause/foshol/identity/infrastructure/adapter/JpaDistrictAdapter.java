package com.rootcause.foshol.identity.infrastructure.adapter;

import com.rootcause.foshol.identity.application.port.DistrictPort;
import com.rootcause.foshol.identity.application.port.DistrictRef;
import com.rootcause.foshol.identity.infrastructure.persistence.GeoDistrictJpaRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class JpaDistrictAdapter implements DistrictPort {

    private final GeoDistrictJpaRepository districts;

    public JpaDistrictAdapter(GeoDistrictJpaRepository districts) {
        this.districts = districts;
    }

    @Override
    public Optional<DistrictRef> findByCode(String districtCode) {
        return districts
                .findById(districtCode)
                .map(d -> new DistrictRef(d.getCode(), d.getDivision().getCode()));
    }
}

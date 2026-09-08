package com.rootcause.foshol.identity.infrastructure;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GeoDistrictJpaRepository extends JpaRepository<GeoDistrictEntity, String> {

    List<GeoDistrictEntity> findByDivision_CodeOrderByNameEnAsc(String divisionCode);
}

package com.rootcause.foshol.identity.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FieldOfficerJpaRepository extends JpaRepository<FieldOfficerEntity, UUID> {

    Optional<FieldOfficerEntity> findByUsername(String username);

    @Query("select o.id as id, o.name as name, o.username as username, o.districtCode as districtCode, o.role as role from FieldOfficerEntity o where o.id = :id")
    Optional<OfficerReadRow> findReadById(UUID id);

    List<FieldOfficerEntity> findByDistrictCodeAndActiveTrueOrderByNameAsc(String districtCode);
}

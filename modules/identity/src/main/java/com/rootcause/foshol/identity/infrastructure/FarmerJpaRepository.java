package com.rootcause.foshol.identity.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FarmerJpaRepository extends JpaRepository<FarmerEntity, UUID> {

    Optional<FarmerEntity> findByPhoneHash(String phoneHash);

    @Query("select f.id as id, f.name as name, f.districtCode as districtCode, f.divisionCode as divisionCode, f.preferredLanguage as preferredLanguage from FarmerEntity f where f.id = :id")
    Optional<FarmerReadRow> findReadById(UUID id);
}

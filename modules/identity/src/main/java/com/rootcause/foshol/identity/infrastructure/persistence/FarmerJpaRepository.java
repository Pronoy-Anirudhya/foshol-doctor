package com.rootcause.foshol.identity.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FarmerJpaRepository extends JpaRepository<FarmerEntity, UUID> {

    Optional<FarmerEntity> findByPhoneHash(String phoneHash);

    Optional<FarmerDirectoryRow> findByPhoneHashAndDistrictCode(String phoneHash, String districtCode);

    Optional<FarmerDirectoryRow> findByIdAndDistrictCode(UUID id, String districtCode);

    Page<FarmerDirectoryRow> findByDistrictCodeOrderByCreatedAtDesc(String districtCode, Pageable pageable);

    Page<FarmerDirectoryRow> findByDistrictCodeAndNameContainingIgnoreCaseOrderByCreatedAtDesc(
            String districtCode, String name, Pageable pageable);

    @Query(
            "select f.id as id, f.name as name, f.districtCode as districtCode, f.divisionCode as divisionCode, f.preferredLanguage as preferredLanguage from FarmerEntity f where f.id = :id")
    Optional<FarmerReadRow> findReadById(UUID id);
}

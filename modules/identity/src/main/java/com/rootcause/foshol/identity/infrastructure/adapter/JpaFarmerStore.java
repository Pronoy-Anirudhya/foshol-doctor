package com.rootcause.foshol.identity.infrastructure.adapter;

import com.rootcause.foshol.identity.application.port.FarmerSnapshot;
import com.rootcause.foshol.identity.application.port.FarmerStore;
import com.rootcause.foshol.identity.infrastructure.persistence.FarmerEntity;
import com.rootcause.foshol.identity.infrastructure.persistence.FarmerJpaRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaFarmerStore implements FarmerStore {

    private final FarmerJpaRepository farmers;

    public JpaFarmerStore(FarmerJpaRepository farmers) {
        this.farmers = farmers;
    }

    @Override
    public Optional<FarmerSnapshot> findByPhoneHash(String phoneHash) {
        return farmers.findByPhoneHash(phoneHash).map(JpaFarmerStore::toSnapshot);
    }

    @Override
    public Optional<FarmerSnapshot> findById(UUID id) {
        return farmers.findById(id).map(JpaFarmerStore::toSnapshot);
    }

    @Override
    public void saveAndFlush(FarmerSnapshot farmer) {
        farmers.saveAndFlush(toEntity(farmer));
    }

    private static FarmerSnapshot toSnapshot(FarmerEntity entity) {
        return new FarmerSnapshot(
                entity.getId(),
                entity.getName(),
                entity.getPhoneHash(),
                null,
                entity.getDistrictCode(),
                entity.getDivisionCode(),
                entity.getPreferredLanguage(),
                entity.getRegisteredBy(),
                entity.getRegistrationSource(),
                entity.getCreatedAt());
    }

    private static FarmerEntity toEntity(FarmerSnapshot farmer) {
        return new FarmerEntity(
                farmer.id(),
                farmer.name(),
                farmer.phoneHash(),
                farmer.phoneEnc(),
                farmer.districtCode(),
                farmer.divisionCode(),
                farmer.preferredLanguage(),
                farmer.registeredBy(),
                farmer.registrationSource(),
                farmer.createdAt(),
                farmer.createdAt());
    }
}

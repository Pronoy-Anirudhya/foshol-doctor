package com.rootcause.foshol.identity.infrastructure;

import com.rootcause.foshol.identity.api.FarmerLookupApi;
import com.rootcause.foshol.identity.api.FarmerView;
import com.rootcause.foshol.identity.domain.PhoneHash;
import com.rootcause.foshol.identity.domain.PhoneNumber;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FarmerLookupApiAdapter implements FarmerLookupApi {

    private final FarmerJpaRepository farmers;

    public FarmerLookupApiAdapter(FarmerJpaRepository farmers) {
        this.farmers = farmers;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FarmerView> findById(UUID farmerId) {
        return farmers.findById(farmerId).map(this::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FarmerView> findByPhone(String e164Phone) {
        PhoneNumber phone = PhoneNumber.parse(e164Phone);
        return farmers.findByPhoneHash(PhoneHash.of(phone).hex()).map(this::toView);
    }

    private FarmerView toView(FarmerEntity entity) {
        return new FarmerView(
                entity.getId(),
                entity.getName(),
                entity.getDistrictCode(),
                entity.getPreferredLanguage(),
                entity.getDivisionCode());
    }
}

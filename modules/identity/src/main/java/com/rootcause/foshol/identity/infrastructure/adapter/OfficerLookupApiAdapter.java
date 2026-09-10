package com.rootcause.foshol.identity.infrastructure.adapter;

import com.rootcause.foshol.identity.api.OfficerLookupApi;
import com.rootcause.foshol.identity.api.OfficerView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.rootcause.foshol.identity.infrastructure.persistence.FieldOfficerEntity;
import com.rootcause.foshol.identity.infrastructure.persistence.FieldOfficerJpaRepository;

@Service
public class OfficerLookupApiAdapter implements OfficerLookupApi {

    private final FieldOfficerJpaRepository officers;

    public OfficerLookupApiAdapter(FieldOfficerJpaRepository officers) {
        this.officers = officers;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OfficerView> findById(UUID officerId) {
        return officers.findById(officerId).map(this::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OfficerView> findActiveByDistrict(String districtCode) {
        return officers.findByDistrictCodeAndActiveTrueOrderByNameAsc(districtCode).stream()
                .map(this::toView)
                .toList();
    }

    private OfficerView toView(FieldOfficerEntity entity) {
        return new OfficerView(
                entity.getId(),
                entity.getName(),
                entity.getDistrictCode(),
                entity.getRole(),
                entity.isActive(),
                entity.getDivisionCode());
    }
}

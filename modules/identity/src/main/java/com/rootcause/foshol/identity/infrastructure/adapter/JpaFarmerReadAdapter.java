package com.rootcause.foshol.identity.infrastructure.adapter;

import com.rootcause.foshol.identity.application.port.FarmerDirectoryPage;
import com.rootcause.foshol.identity.application.port.FarmerDirectorySnapshot;
import com.rootcause.foshol.identity.application.port.FarmerReadPort;
import com.rootcause.foshol.identity.application.port.FarmerReadSnapshot;
import com.rootcause.foshol.identity.infrastructure.persistence.FarmerDirectoryRow;
import com.rootcause.foshol.identity.infrastructure.persistence.FarmerJpaRepository;
import com.rootcause.foshol.identity.infrastructure.persistence.FarmerReadRow;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
public class JpaFarmerReadAdapter implements FarmerReadPort {

    private final FarmerJpaRepository farmers;

    public JpaFarmerReadAdapter(FarmerJpaRepository farmers) {
        this.farmers = farmers;
    }

    @Override
    public Optional<FarmerReadSnapshot> findReadById(UUID id) {
        return farmers.findReadById(id).map(JpaFarmerReadAdapter::toRead);
    }

    @Override
    public Optional<FarmerDirectorySnapshot> findByIdAndDistrictCode(UUID id, String districtCode) {
        return farmers.findByIdAndDistrictCode(id, districtCode).map(JpaFarmerReadAdapter::toDirectory);
    }

    @Override
    public Optional<FarmerDirectorySnapshot> findByPhoneHashAndDistrictCode(String phoneHash, String districtCode) {
        return farmers.findByPhoneHashAndDistrictCode(phoneHash, districtCode).map(JpaFarmerReadAdapter::toDirectory);
    }

    @Override
    public FarmerDirectoryPage findByDistrictCode(String districtCode, int page, int size) {
        return toPage(farmers.findByDistrictCodeOrderByCreatedAtDesc(districtCode, PageRequest.of(page, size)));
    }

    @Override
    public FarmerDirectoryPage findByDistrictCodeAndName(String districtCode, String name, int page, int size) {
        return toPage(farmers.findByDistrictCodeAndNameContainingIgnoreCaseOrderByCreatedAtDesc(
                districtCode, name, PageRequest.of(page, size)));
    }

    private static FarmerDirectoryPage toPage(Page<FarmerDirectoryRow> result) {
        return new FarmerDirectoryPage(
                result.getContent().stream().map(JpaFarmerReadAdapter::toDirectory).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    private static FarmerReadSnapshot toRead(FarmerReadRow row) {
        return new FarmerReadSnapshot(
                row.getId(), row.getName(), row.getDistrictCode(), row.getDivisionCode(), row.getPreferredLanguage());
    }

    private static FarmerDirectorySnapshot toDirectory(FarmerDirectoryRow row) {
        return new FarmerDirectorySnapshot(
                row.getId(),
                row.getName(),
                row.getDistrictCode(),
                row.getDivisionCode(),
                row.getPreferredLanguage(),
                row.getCreatedAt(),
                row.getRegisteredBy(),
                row.getRegistrationSource());
    }
}

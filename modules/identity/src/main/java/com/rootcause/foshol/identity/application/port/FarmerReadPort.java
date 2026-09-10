package com.rootcause.foshol.identity.application.port;

import java.util.Optional;
import java.util.UUID;

public interface FarmerReadPort {

    Optional<FarmerReadSnapshot> findReadById(UUID id);

    Optional<FarmerDirectorySnapshot> findByIdAndDistrictCode(UUID id, String districtCode);

    Optional<FarmerDirectorySnapshot> findByPhoneHashAndDistrictCode(String phoneHash, String districtCode);

    FarmerDirectoryPage findByDistrictCode(String districtCode, int page, int size);

    FarmerDirectoryPage findByDistrictCodeAndName(String districtCode, String name, int page, int size);
}

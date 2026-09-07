package com.rootcause.foshol.identity.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OfficerLookupApi {

    Optional<OfficerView> findById(UUID officerId);

    List<OfficerView> findActiveByDistrict(String districtCode);
}

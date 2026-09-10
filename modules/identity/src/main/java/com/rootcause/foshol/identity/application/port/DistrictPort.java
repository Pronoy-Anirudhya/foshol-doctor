package com.rootcause.foshol.identity.application.port;

import java.util.Optional;

public interface DistrictPort {

    Optional<DistrictRef> findByCode(String districtCode);
}

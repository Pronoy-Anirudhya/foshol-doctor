package com.rootcause.foshol.identity.application.port;

import com.rootcause.foshol.identity.application.query.DistrictResponse;
import com.rootcause.foshol.identity.application.query.DivisionResponse;
import java.util.List;
import java.util.Optional;

public interface GeoCataloguePort {

    List<DivisionResponse> listDivisions();

    Optional<List<DistrictResponse>> listDistricts(String divisionCode);
}

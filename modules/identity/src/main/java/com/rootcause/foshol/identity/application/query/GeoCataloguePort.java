package com.rootcause.foshol.identity.application.query;

import java.util.List;
import java.util.Optional;

public interface GeoCataloguePort {

    List<DivisionResponse> listDivisions();

    Optional<List<DistrictResponse>> listDistricts(String divisionCode);
}

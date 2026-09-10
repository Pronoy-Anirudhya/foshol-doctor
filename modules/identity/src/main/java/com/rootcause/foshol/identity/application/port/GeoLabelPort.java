package com.rootcause.foshol.identity.application.port;

public interface GeoLabelPort {

    GeoLabels forDistrict(String districtCode, String divisionCodeFallback);
}

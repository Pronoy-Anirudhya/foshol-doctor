package com.rootcause.foshol.identity.infrastructure.persistence;

import java.util.UUID;

public interface FarmerReadRow {

    UUID getId();

    String getName();

    String getDistrictCode();

    String getDivisionCode();

    String getPreferredLanguage();
}

package com.rootcause.foshol.identity.infrastructure;

import java.util.UUID;

public interface FarmerReadRow {

    UUID getId();

    String getName();

    String getDistrictCode();

    String getPreferredLanguage();
}

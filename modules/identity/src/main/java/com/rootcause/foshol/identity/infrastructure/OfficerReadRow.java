package com.rootcause.foshol.identity.infrastructure;

import java.util.UUID;

public interface OfficerReadRow {

    UUID getId();

    String getName();

    String getUsername();

    String getDistrictCode();

    String getDivisionCode();

    String getRole();
}

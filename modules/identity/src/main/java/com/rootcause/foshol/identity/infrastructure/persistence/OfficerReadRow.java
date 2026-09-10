package com.rootcause.foshol.identity.infrastructure.persistence;

import java.util.UUID;

public interface OfficerReadRow {

    UUID getId();

    String getName();

    String getUsername();

    String getDistrictCode();

    String getDivisionCode();

    String getRole();
}

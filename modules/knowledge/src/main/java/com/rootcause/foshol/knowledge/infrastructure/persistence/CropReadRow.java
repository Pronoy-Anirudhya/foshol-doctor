package com.rootcause.foshol.knowledge.infrastructure.persistence;

import java.util.UUID;

public interface CropReadRow {

    UUID getId();

    String getCode();

    String getNameBn();

    String getNameEn();

    String getIconKey();

    Short getDisplayOrder();
}

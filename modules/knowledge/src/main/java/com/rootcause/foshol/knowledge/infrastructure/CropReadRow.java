package com.rootcause.foshol.knowledge.infrastructure;

import java.util.UUID;

public interface CropReadRow {

    UUID getId();

    String getCode();

    String getNameBn();

    String getNameEn();

    String getIconKey();

    Short getDisplayOrder();
}

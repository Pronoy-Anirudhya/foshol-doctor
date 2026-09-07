package com.rootcause.foshol.knowledge.infrastructure;

import java.util.UUID;

public interface SymptomReadRow {

    UUID getId();

    String getCode();

    String getNameBn();

    String getNameEn();

    String getOrgan();
}

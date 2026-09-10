package com.rootcause.foshol.knowledge.infrastructure.persistence;

import com.rootcause.foshol.common.enums.Severity;
import java.util.UUID;

public interface DiseaseReadRow {

    UUID getId();

    UUID getCropId();

    String getCode();

    String getNameBn();

    String getNameEn();

    String getDescriptionBn();

    String getDescriptionEn();

    Severity getSeverity();

    boolean getHealthy();
}

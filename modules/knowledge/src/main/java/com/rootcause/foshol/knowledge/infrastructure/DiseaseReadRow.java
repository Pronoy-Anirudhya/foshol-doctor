package com.rootcause.foshol.knowledge.infrastructure;

import com.rootcause.foshol.common.Severity;
import java.util.UUID;

public interface DiseaseReadRow {

    UUID getId();

    UUID getCropId();

    String getCode();

    String getNameBn();

    String getNameEn();

    String getDescriptionBn();

    Severity getSeverity();

    boolean getHealthy();
}

package com.rootcause.foshol.knowledge.infrastructure.persistence;

import com.rootcause.foshol.common.enums.RemedyRateBasis;
import com.rootcause.foshol.common.enums.RemedyRateUnit;
import com.rootcause.foshol.common.enums.RemedyType;
import java.math.BigDecimal;
import java.util.UUID;

public interface RemedyReadRow {

    UUID getId();

    UUID getDiseaseId();

    RemedyType getType();

    String getTitleBn();

    String getStepsBn();

    String getDosageBn();

    Short getPhiDays();

    String getCostTier();

    String getEfficacy();

    String getSourceRef();

    Short getDisplayOrder();

    BigDecimal getRateAmount();

    RemedyRateUnit getRateUnit();

    RemedyRateBasis getRateBasis();

    String getRateNotesBn();
}

package com.rootcause.foshol.knowledge.infrastructure;

import com.rootcause.foshol.common.RemedyRateBasis;
import com.rootcause.foshol.common.RemedyRateUnit;
import com.rootcause.foshol.common.RemedyType;
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

package com.rootcause.foshol.knowledge.infrastructure;

import com.rootcause.foshol.common.RemedyType;
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
}

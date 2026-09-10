package com.rootcause.foshol.intake.application.query;

import com.rootcause.foshol.common.enums.CaseStatus;
import com.rootcause.foshol.common.enums.DecisionPath;
import java.time.Instant;
import java.util.UUID;

public record FarmerCaseRow(
        UUID caseId,
        String cropNameBn,
        String cropNameEn,
        boolean cropNameEnFallback,
        CaseStatus status,
        DecisionPath decisionPath,
        String diseaseNameBn,
        String diseaseNameEn,
        boolean diseaseNameEnFallback,
        String officerName,
        Integer advisoryVersion,
        String rejectionMessageBn,
        UUID thumbnailImageId,
        Instant submittedAt,
        Instant publishedAt) {}

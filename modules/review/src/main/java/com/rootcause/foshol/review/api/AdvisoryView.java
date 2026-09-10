package com.rootcause.foshol.review.api;

import com.rootcause.foshol.common.enums.AdvisoryAction;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdvisoryView(
        UUID advisoryId,
        UUID caseId,
        UUID diseaseId,
        String diseaseNameBn,
        UUID officerId,
        String officerName,
        AdvisoryAction action,
        String officerNoteBn,
        int version,
        UUID supersedesId,
        List<RemedyRefView> remedies,
        Instant publishedAt) {}

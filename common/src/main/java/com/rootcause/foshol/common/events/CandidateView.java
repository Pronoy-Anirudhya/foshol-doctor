package com.rootcause.foshol.common.events;

import com.rootcause.foshol.common.enums.CandidateSource;
import java.math.BigDecimal;
import java.util.UUID;

public record CandidateView(
        UUID diseaseId,
        String diseaseCode,
        String diseaseNameBn,
        BigDecimal confidence,
        int rank,
        CandidateSource source) {}

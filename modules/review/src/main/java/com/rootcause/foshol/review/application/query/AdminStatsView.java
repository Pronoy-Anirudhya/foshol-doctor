package com.rootcause.foshol.review.application.query;

import java.math.BigDecimal;

public record AdminStatsView(
        long casesToday,
        BigDecimal approvalRate,
        BigDecimal medianReviewMinutes,
        BigDecimal agreementRate,
        long agreementSampleSize,
        BigDecimal confidenceHigh,
        BigDecimal confidenceLow) {}

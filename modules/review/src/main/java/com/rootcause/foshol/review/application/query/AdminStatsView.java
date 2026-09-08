package com.rootcause.foshol.review.application.query;

import java.math.BigDecimal;

public record AdminStatsView(
        long casesToday,
        long casesThisMonth,
        long casesThisYear,
        long casesLifetime,
        BigDecimal approvalRate,
        BigDecimal medianReviewMinutes,
        BigDecimal agreementRate,
        long agreementSampleSize,
        BigDecimal rejectionRate,
        BigDecimal confidenceHigh,
        BigDecimal confidenceLow) {}

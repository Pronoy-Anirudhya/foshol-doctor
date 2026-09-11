package com.rootcause.foshol.review.web;

import com.rootcause.foshol.common.contract.ConfigKeys;
import com.rootcause.foshol.review.application.query.ReviewTaskDetailView;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ReviewCaseDetailMapper {

    private final BigDecimal confidenceHigh;
    private final BigDecimal confidenceLow;

    public ReviewCaseDetailMapper(
            @Value("${" + ConfigKeys.ANALYSIS_CONFIDENCE_HIGH + "}") BigDecimal confidenceHigh,
            @Value("${" + ConfigKeys.ANALYSIS_CONFIDENCE_LOW + "}") BigDecimal confidenceLow) {
        this.confidenceHigh = confidenceHigh;
        this.confidenceLow = confidenceLow;
    }

    public ReviewCaseDetailResponse map(ReviewTaskDetailView view) {
        return ReviewCaseDetailResponse.from(view, confidenceHigh, confidenceLow);
    }
}

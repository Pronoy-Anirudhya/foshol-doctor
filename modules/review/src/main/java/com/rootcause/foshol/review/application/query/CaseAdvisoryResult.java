package com.rootcause.foshol.review.application.query;

import com.rootcause.foshol.review.api.AdvisoryView;
import com.rootcause.foshol.review.api.RejectionView;
import java.util.List;

public record CaseAdvisoryResult(
        AdvisoryView published, List<AdvisoryView> history, RejectionView rejection) {}

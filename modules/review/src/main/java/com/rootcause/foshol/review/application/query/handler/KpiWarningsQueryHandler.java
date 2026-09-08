package com.rootcause.foshol.review.application.query.handler;

import com.rootcause.foshol.review.application.ReviewKpiCalendar;
import com.rootcause.foshol.review.application.port.ReviewQueryPort;
import com.rootcause.foshol.review.application.query.KpiWarningView;
import com.rootcause.foshol.review.application.query.KpiWarningsQuery;
import com.rootcause.foshol.common.cqrs.QueryHandler;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KpiWarningsQueryHandler implements QueryHandler<KpiWarningsQuery, List<KpiWarningView>> {

    @Override
    public Class<KpiWarningsQuery> queryType() {
        return KpiWarningsQuery.class;
    }

    private final ReviewQueryPort reads;
    private final ReviewKpiCalendar kpi;
    private final Clock clock;

    public KpiWarningsQueryHandler(ReviewQueryPort reads, ReviewKpiCalendar kpi, Clock clock) {
        this.reads = reads;
        this.kpi = kpi;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @Override
    public List<KpiWarningView> handle(KpiWarningsQuery query) {
        return reads.findOpenResolutionWarnings(query.callerId(), clock.instant(), kpi.warnBefore());
    }
}

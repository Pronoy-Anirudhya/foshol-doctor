package com.rootcause.foshol.review.web;

import com.rootcause.foshol.common.cqrs.QueryBus;
import com.rootcause.foshol.review.application.query.KpiWarningsQuery;
import com.rootcause.foshol.review.application.query.KpiWarningView;
import com.rootcause.foshol.review.application.query.ListDistrictOfficersQuery;
import com.rootcause.foshol.review.application.query.ColleagueOfficerView;
import com.rootcause.foshol.review.application.query.OfficerQueuePage;
import com.rootcause.foshol.review.application.query.OfficerQueueQuery;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/review")
public class ReviewQueueController {

    private final QueryBus queries;

    public ReviewQueueController(QueryBus queries) {
        this.queries = queries;
    }

    @GetMapping("/queue")
    @PreAuthorize("hasAnyRole('OFFICER','ADMIN')")
    public OfficerQueuePage queue(
            @RequestParam(name = "state", required = false) String state,
            @RequestParam(name = "mine", required = false, defaultValue = "false") boolean mine,
            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
            @RequestParam(name = "size", required = false, defaultValue = "20") int size,
            @RequestParam(name = "sort", required = false) String sort,
            @RequestParam(name = "order", required = false) String order,
            Authentication authentication) {
        var officerId = ReviewAuth.subjectId(authentication);
        return queries.handle(new OfficerQueueQuery(state, mine, officerId, null, page, size, sort, order));
    }

    @GetMapping("/officers")
    @PreAuthorize("hasAnyRole('OFFICER','ADMIN')")
    public List<ColleagueOfficerView> officers(Authentication authentication) {
        return queries.handle(new ListDistrictOfficersQuery(ReviewAuth.subjectId(authentication)));
    }

    @GetMapping("/kpi-warnings")
    @PreAuthorize("hasAnyRole('OFFICER','ADMIN')")
    public List<KpiWarningView> kpiWarnings(Authentication authentication) {
        return queries.handle(new KpiWarningsQuery(ReviewAuth.subjectId(authentication)));
    }
}

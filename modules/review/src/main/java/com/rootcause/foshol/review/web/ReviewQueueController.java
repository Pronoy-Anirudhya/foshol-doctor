package com.rootcause.foshol.review.web;

import com.rootcause.foshol.review.application.query.OfficerQueuePage;
import com.rootcause.foshol.review.application.query.OfficerQueueQuery;
import com.rootcause.foshol.review.application.query.OfficerQueueQueryHandler;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/review")
public class ReviewQueueController {

    private final OfficerQueueQueryHandler queueQuery;

    public ReviewQueueController(OfficerQueueQueryHandler queueQuery) {
        this.queueQuery = queueQuery;
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
        return queueQuery.handle(new OfficerQueueQuery(
                state, mine, ReviewAuth.subjectId(authentication), page, size, sort, order));
    }
}

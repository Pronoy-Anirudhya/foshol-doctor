package com.rootcause.foshol.review.web;

import com.rootcause.foshol.review.application.query.AdminStatsQuery;
import com.rootcause.foshol.review.application.query.AdminStatsQueryHandler;
import com.rootcause.foshol.review.application.query.AdminStatsView;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminStatsController {

    private final AdminStatsQueryHandler stats;

    public AdminStatsController(AdminStatsQueryHandler stats) {
        this.stats = stats;
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminStatsView stats() {
        return stats.handle(new AdminStatsQuery());
    }
}

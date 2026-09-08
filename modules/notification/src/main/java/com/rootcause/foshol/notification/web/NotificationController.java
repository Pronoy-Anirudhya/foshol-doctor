package com.rootcause.foshol.notification.web;

import com.rootcause.foshol.common.cqrs.QueryBus;
import com.rootcause.foshol.notification.application.query.FarmerNotificationsPage;
import com.rootcause.foshol.notification.application.query.FarmerNotificationsQuery;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class NotificationController {

    private final QueryBus queries;

    public NotificationController(QueryBus queries) {
        this.queries = queries;
    }

    @GetMapping("/notifications")
    @PreAuthorize("hasRole('FARMER')")
    public FarmerNotificationsPage list(
            Authentication authentication,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return queries.handle(new FarmerNotificationsQuery(UUID.fromString(authentication.getName()), page, size));
    }
}

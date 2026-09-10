package com.rootcause.foshol.review.application.query.handler;

import com.rootcause.foshol.identity.api.OfficerLookupApi;
import com.rootcause.foshol.knowledge.api.KnowledgeQueryApi;
import com.rootcause.foshol.review.application.port.KpiBreachPort;
import com.rootcause.foshol.review.application.port.KpiBreachPort.KpiBreachRow;
import com.rootcause.foshol.review.application.query.AdminKpiBreachPage;
import com.rootcause.foshol.review.application.query.AdminKpiBreachPage.AdminKpiBreachRow;
import com.rootcause.foshol.review.application.query.AdminKpiBreachesQuery;
import com.rootcause.foshol.review.application.query.CatalogueTextEnricher;
import com.rootcause.foshol.review.application.query.CatalogueTextEnricher.Named;
import com.rootcause.foshol.review.domain.ReviewException;
import com.rootcause.foshol.common.cqrs.QueryHandler;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminKpiBreachesQueryHandler implements QueryHandler<AdminKpiBreachesQuery, AdminKpiBreachPage> {

    @Override
    public Class<AdminKpiBreachesQuery> queryType() {
        return AdminKpiBreachesQuery.class;
    }

    private final KpiBreachPort breaches;
    private final OfficerLookupApi officers;
    private final KnowledgeQueryApi knowledge;

    public AdminKpiBreachesQueryHandler(
            KpiBreachPort breaches, OfficerLookupApi officers, KnowledgeQueryApi knowledge) {
        this.breaches = breaches;
        this.officers = officers;
        this.knowledge = knowledge;
    }

    @Transactional(readOnly = true)
    @Override
    public AdminKpiBreachPage handle(AdminKpiBreachesQuery query) {
        String district = officers
                .findById(query.callerId())
                .map(o -> o.districtCode())
                .orElseThrow(ReviewException::taskNotFound);
        int size = query.size() <= 0 ? 20 : Math.min(query.size(), 100);
        int page = Math.max(query.page(), 0);
        long total = breaches.countBreaches(district, query.kind(), query.officerId());
        int totalPages = size == 0 ? 0 : (int) Math.ceil(total / (double) size);
        CatalogueTextEnricher enricher = new CatalogueTextEnricher(knowledge);
        List<AdminKpiBreachRow> content = breaches.findBreaches(district, query.kind(), query.officerId(), page, size)
                .stream()
                .map(row -> toRow(row, enricher))
                .toList();
        return new AdminKpiBreachPage(content, page, size, total, totalPages);
    }

    private AdminKpiBreachRow toRow(KpiBreachRow row, CatalogueTextEnricher enricher) {
        String officerName = row.officerId() == null
                ? null
                : officers.findById(row.officerId()).map(o -> o.name()).orElse("");
        Named crop = enricher.crop(row.cropCode(), row.cropNameBn());
        return new AdminKpiBreachRow(
                row.id(),
                row.reviewTaskId(),
                row.caseId(),
                row.kind(),
                row.officerId(),
                officerName,
                row.farmerName(),
                row.cropCode(),
                crop.bn(),
                crop.en(),
                crop.fallback(),
                row.dueAt(),
                row.breachedAt());
    }
}

package com.rootcause.foshol.review.application.query.handler;

import com.rootcause.foshol.identity.api.OfficerLookupApi;
import com.rootcause.foshol.knowledge.api.KnowledgeQueryApi;
import com.rootcause.foshol.review.application.port.ReviewQueryPort;
import com.rootcause.foshol.review.application.query.CatalogueTextEnricher;
import com.rootcause.foshol.review.application.query.OfficerQueuePage;
import com.rootcause.foshol.review.application.query.OfficerQueueQuery;
import com.rootcause.foshol.review.domain.ReviewException;
import com.rootcause.foshol.common.cqrs.QueryHandler;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OfficerQueueQueryHandler implements QueryHandler<OfficerQueueQuery, OfficerQueuePage> {

    @Override
    public Class<OfficerQueueQuery> queryType() {
        return OfficerQueueQuery.class;
    }

    private final ReviewQueryPort reads;
    private final OfficerLookupApi officers;
    private final KnowledgeQueryApi knowledge;

    public OfficerQueueQueryHandler(ReviewQueryPort reads, OfficerLookupApi officers, KnowledgeQueryApi knowledge) {
        this.reads = reads;
        this.officers = officers;
        this.knowledge = knowledge;
    }

    @Transactional(readOnly = true)
    @Override
    public OfficerQueuePage handle(OfficerQueueQuery query) {
        if (query.sort() != null || query.order() != null) {
            throw ReviewException.queueSortNotSupported();
        }
        String district = query.districtCode();
        if (district == null || district.isBlank()) {
            district = officers
                    .findById(query.officerId())
                    .map(o -> o.districtCode())
                    .orElseThrow(ReviewException::taskNotFound);
        }
        OfficerQueuePage page = reads.findQueue(new OfficerQueueQuery(
                query.state(),
                query.mine(),
                query.officerId(),
                district,
                query.page(),
                query.size(),
                query.sort(),
                query.order()));
        CatalogueTextEnricher enricher = new CatalogueTextEnricher(knowledge);
        return new OfficerQueuePage(
                page.content().stream().map(enricher::enrich).toList(),
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages());
    }
}

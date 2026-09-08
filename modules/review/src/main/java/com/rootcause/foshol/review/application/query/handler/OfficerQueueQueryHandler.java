package com.rootcause.foshol.review.application.query.handler;

import com.rootcause.foshol.identity.api.OfficerLookupApi;
import com.rootcause.foshol.review.application.port.ReviewQueryPort;
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

    public OfficerQueueQueryHandler(ReviewQueryPort reads, OfficerLookupApi officers) {
        this.reads = reads;
        this.officers = officers;
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
        return reads.findQueue(new OfficerQueueQuery(
                query.state(),
                query.mine(),
                query.officerId(),
                district,
                query.page(),
                query.size(),
                query.sort(),
                query.order()));
    }
}

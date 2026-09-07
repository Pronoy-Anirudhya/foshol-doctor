package com.rootcause.foshol.review.application.query.handler;

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

    public OfficerQueueQueryHandler(ReviewQueryPort reads) {
        this.reads = reads;
    }

    @Transactional(readOnly = true)
    @Override
    public OfficerQueuePage handle(OfficerQueueQuery query) {
        if (query.sort() != null || query.order() != null) {
            throw ReviewException.queueSortNotSupported();
        }
        return reads.findQueue(query);
    }
}

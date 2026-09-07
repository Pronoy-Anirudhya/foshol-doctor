package com.rootcause.foshol.review.application.query;

import com.rootcause.foshol.review.application.port.ReviewQueryPort;
import com.rootcause.foshol.review.domain.ReviewException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OfficerQueueQueryHandler {

    private final ReviewQueryPort reads;

    public OfficerQueueQueryHandler(ReviewQueryPort reads) {
        this.reads = reads;
    }

    @Transactional(readOnly = true)
    public OfficerQueuePage handle(OfficerQueueQuery query) {
        if (query.sort() != null || query.order() != null) {
            throw ReviewException.queueSortNotSupported();
        }
        return reads.findQueue(query);
    }
}

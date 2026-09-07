package com.rootcause.foshol.common.cqrs;

import com.rootcause.foshol.common.CorrelationId;

public interface Query {

    default String tracerId() {
        return CorrelationId.currentOrCreate();
    }
}

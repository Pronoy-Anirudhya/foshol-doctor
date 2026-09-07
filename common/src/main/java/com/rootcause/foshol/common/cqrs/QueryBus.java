package com.rootcause.foshol.common.cqrs;

import com.rootcause.foshol.common.CorrelationId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class QueryBus {

    private final Map<Class<?>, QueryHandler<?, ?>> queryHandlerMap = new ConcurrentHashMap<>();

    public void register(Class<?> queryType, QueryHandler<?, ?> handler) {
        QueryHandler<?, ?> existing = queryHandlerMap.putIfAbsent(queryType, handler);
        if (existing != null) {
            throw new IllegalStateException(
                    "Duplicate QueryHandler registration for: " + queryType.getName()
                            + " | existing: " + existing.getClass().getName()
                            + " | new: " + handler.getClass().getName());
        }
    }

    @SuppressWarnings("unchecked")
    public <Q extends Query, R> R handle(Q query) {
        QueryHandler<Q, R> handler = (QueryHandler<Q, R>) queryHandlerMap.get(query.getClass());
        if (handler == null) {
            throw new IllegalArgumentException("No handler found for query: " + query.getClass());
        }
        String previous = CorrelationId.current();
        try {
            CorrelationId.set(query.tracerId());
            return handler.handle(query);
        } finally {
            if (previous == null || previous.isBlank()) {
                CorrelationId.clear();
            } else {
                CorrelationId.set(previous);
            }
        }
    }
}

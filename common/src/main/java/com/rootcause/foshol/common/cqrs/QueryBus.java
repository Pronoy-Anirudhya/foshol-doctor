package com.rootcause.foshol.common.cqrs;

import com.rootcause.foshol.common.util.CorrelationId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

@Slf4j
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
        String name = query.getClass().getSimpleName();
        String previous = CorrelationId.current();
        long started = System.nanoTime();
        try {
            CorrelationId.set(query.tracerId());
            log.info("query start {}", name);
            R result = handler.handle(query);
            log.info("query done {} ({} ms)", name, elapsedMs(started));
            return result;
        } catch (RuntimeException ex) {
            log.info("query failed {} ({} ms): {}", name, elapsedMs(started), ex.getClass().getSimpleName());
            throw ex;
        } finally {
            if (previous == null || previous.isBlank()) {
                CorrelationId.clear();
            } else {
                CorrelationId.set(previous);
            }
        }
    }

    private static long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }
}

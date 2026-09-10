package com.rootcause.foshol.common.cqrs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rootcause.foshol.common.util.CorrelationId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class QueryBusTest {

    @AfterEach
    void clearMdc() {
        CorrelationId.clear();
    }

    @Test
    void handleDispatchesToRegisteredHandler() {
        QueryBus bus = new QueryBus();
        bus.register(PingQuery.class, new PingHandler());
        int n = bus.handle(new PingQuery(3));
        assertThat(n).isEqualTo(3);
    }

    @Test
    void duplicateRegistrationFails() {
        QueryBus bus = new QueryBus();
        bus.register(PingQuery.class, new PingHandler());
        assertThatThrownBy(() -> bus.register(PingQuery.class, new PingHandler()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(PingQuery.class.getName());
    }

    @Test
    void missingHandlerFails() {
        QueryBus bus = new QueryBus();
        assertThatThrownBy(() -> bus.handle(new PingQuery(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(PingQuery.class.getName());
    }

    @Test
    void restoresPreviousCorrelationId() {
        CorrelationId.set("before");
        QueryBus bus = new QueryBus();
        bus.register(PingQuery.class, new QueryHandler<PingQuery, Integer>() {
            @Override
            public Class<PingQuery> queryType() {
                return PingQuery.class;
            }

            @Override
            public Integer handle(PingQuery query) {
                assertThat(CorrelationId.current()).isEqualTo("during");
                return query.n();
            }
        });
        int n = bus.handle(new PingQuery(1));
        assertThat(n).isEqualTo(1);
        assertThat(CorrelationId.current()).isEqualTo("before");
    }

    record PingQuery(int n) implements Query {
        @Override
        public String tracerId() {
            return "during";
        }
    }

    static final class PingHandler implements QueryHandler<PingQuery, Integer> {
        @Override
        public Class<PingQuery> queryType() {
            return PingQuery.class;
        }

        @Override
        public Integer handle(PingQuery query) {
            return query.n();
        }
    }
}

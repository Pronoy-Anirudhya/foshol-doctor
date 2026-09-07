package com.rootcause.foshol;

import static org.assertj.core.api.Assertions.assertThat;

import com.rootcause.foshol.common.cqrs.Query;
import com.rootcause.foshol.common.cqrs.QueryBus;
import com.rootcause.foshol.common.cqrs.QueryHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class CqrsConfigurationCycleTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(CqrsConfiguration.class, CycleFixture.class);

    @Test
    void startsWithAdapterDependingOnBusAndHandlerDependingOnAdapter() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getEnvironment().getProperty("spring.main.allow-circular-references"))
                    .isNull();

            context.getBean("registerCqrsHandlers", ApplicationRunner.class)
                    .run(new DefaultApplicationArguments());

            KnowledgeLikeApi api = context.getBean(KnowledgeLikeApi.class);
            assertThat(api.find()).isEqualTo("pong");
        });
    }

    @Configuration
    static class CycleFixture {

        @Bean
        KnowledgeLikeApi knowledgeLikeApi(QueryBus queries) {
            return new KnowledgeLikeApi(queries);
        }

        @Bean
        QueryHandler<PingQuery, String> pingQueryHandler(KnowledgeLikeApi api) {
            return new CaseAdvisoryLikeHandler(api);
        }
    }

    static final class KnowledgeLikeApi {

        private final QueryBus queries;

        KnowledgeLikeApi(QueryBus queries) {
            this.queries = queries;
        }

        String find() {
            return queries.handle(new PingQuery());
        }
    }

    record PingQuery() implements Query {}

    static final class CaseAdvisoryLikeHandler implements QueryHandler<PingQuery, String> {

        private final KnowledgeLikeApi api;

        CaseAdvisoryLikeHandler(KnowledgeLikeApi api) {
            this.api = api;
        }

        @Override
        public Class<PingQuery> queryType() {
            return PingQuery.class;
        }

        @Override
        public String handle(PingQuery query) {
            return api != null ? "pong" : "missing";
        }
    }
}

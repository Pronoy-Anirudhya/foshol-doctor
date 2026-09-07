package com.rootcause.foshol.review;

import com.rootcause.foshol.common.cqrs.CommandBus;
import com.rootcause.foshol.common.cqrs.CommandHandler;
import com.rootcause.foshol.common.cqrs.CqrsBuses;
import com.rootcause.foshol.common.cqrs.QueryBus;
import com.rootcause.foshol.common.cqrs.QueryHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(
        scanBasePackages = "com.rootcause.foshol.review",
        exclude = {SecurityAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class})
@EntityScan(basePackages = "com.rootcause.foshol.review")
@EnableJpaRepositories(basePackages = "com.rootcause.foshol.review")
@EnableScheduling
public class ReviewModuleTestApplication {

    @Bean
    CommandBus commandBus() {
        return new CommandBus();
    }

    @Bean
    QueryBus queryBus() {
        return new QueryBus();
    }

    @Bean
    ApplicationRunner registerCqrsHandlers(
            CommandBus commands,
            QueryBus queries,
            ObjectProvider<CommandHandler<?, ?>> commandHandlers,
            ObjectProvider<QueryHandler<?, ?>> queryHandlers) {
        return args -> {
            CqrsBuses.registerCommands(commands, commandHandlers.orderedStream().toList());
            CqrsBuses.registerQueries(queries, queryHandlers.orderedStream().toList());
        };
    }
}

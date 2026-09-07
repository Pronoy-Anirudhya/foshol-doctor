package com.rootcause.foshol;

import com.rootcause.foshol.common.cqrs.CommandBus;
import com.rootcause.foshol.common.cqrs.CommandHandler;
import com.rootcause.foshol.common.cqrs.CqrsBuses;
import com.rootcause.foshol.common.cqrs.QueryBus;
import com.rootcause.foshol.common.cqrs.QueryHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CqrsConfiguration {

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

package com.rootcause.foshol;

import com.rootcause.foshol.common.cqrs.CommandBus;
import com.rootcause.foshol.common.cqrs.CommandHandler;
import com.rootcause.foshol.common.cqrs.CqrsBuses;
import com.rootcause.foshol.common.cqrs.QueryBus;
import com.rootcause.foshol.common.cqrs.QueryHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CqrsConfiguration {

    @Bean
    CommandBus commandBus(ObjectProvider<CommandHandler<?, ?>> handlers) {
        return CqrsBuses.commandBus(handlers.orderedStream().toList());
    }

    @Bean
    QueryBus queryBus(ObjectProvider<QueryHandler<?, ?>> handlers) {
        return CqrsBuses.queryBus(handlers.orderedStream().toList());
    }
}

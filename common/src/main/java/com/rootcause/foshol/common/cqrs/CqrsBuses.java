package com.rootcause.foshol.common.cqrs;

import java.util.List;

public final class CqrsBuses {

    private CqrsBuses() {}

    public static CommandBus commandBus(List<CommandHandler<?, ?>> handlers) {
        CommandBus bus = new CommandBus();
        if (handlers != null) {
            for (CommandHandler<?, ?> handler : handlers) {
                bus.register(handler.commandType(), handler);
            }
        }
        return bus;
    }

    public static QueryBus queryBus(List<QueryHandler<?, ?>> handlers) {
        QueryBus bus = new QueryBus();
        if (handlers != null) {
            for (QueryHandler<?, ?> handler : handlers) {
                bus.register(handler.queryType(), handler);
            }
        }
        return bus;
    }
}

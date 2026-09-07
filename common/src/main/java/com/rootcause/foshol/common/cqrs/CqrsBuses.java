package com.rootcause.foshol.common.cqrs;

import java.util.List;

public final class CqrsBuses {

    private CqrsBuses() {}

    public static CommandBus commandBus(List<CommandHandler<?, ?>> handlers) {
        CommandBus bus = new CommandBus();
        registerCommands(bus, handlers);
        return bus;
    }

    public static QueryBus queryBus(List<QueryHandler<?, ?>> handlers) {
        QueryBus bus = new QueryBus();
        registerQueries(bus, handlers);
        return bus;
    }

    public static void registerCommands(CommandBus bus, List<CommandHandler<?, ?>> handlers) {
        if (handlers == null) {
            return;
        }
        for (CommandHandler<?, ?> handler : handlers) {
            bus.register(handler.commandType(), handler);
        }
    }

    public static void registerQueries(QueryBus bus, List<QueryHandler<?, ?>> handlers) {
        if (handlers == null) {
            return;
        }
        for (QueryHandler<?, ?> handler : handlers) {
            bus.register(handler.queryType(), handler);
        }
    }
}

package com.rootcause.foshol.common.cqrs;

import com.rootcause.foshol.common.CorrelationId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CommandBus {

    private final Map<Class<?>, CommandHandler<?, ?>> commandHandlerMap = new ConcurrentHashMap<>();

    public void register(Class<?> commandType, CommandHandler<?, ?> handler) {
        CommandHandler<?, ?> existing = commandHandlerMap.putIfAbsent(commandType, handler);
        if (existing != null) {
            throw new IllegalStateException(
                    "Duplicate CommandHandler registration for: " + commandType.getName()
                            + " | existing: " + existing.getClass().getName()
                            + " | new: " + handler.getClass().getName());
        }
    }

    @SuppressWarnings("unchecked")
    public <C extends Command, R> R handle(C command) {
        CommandHandler<C, R> handler = (CommandHandler<C, R>) commandHandlerMap.get(command.getClass());
        if (handler == null) {
            throw new IllegalArgumentException("No handler found for command: " + command.getClass());
        }
        String previous = CorrelationId.current();
        try {
            CorrelationId.set(command.tracerId());
            return handler.handle(command);
        } finally {
            restoreMdc(previous);
        }
    }

    private static void restoreMdc(String previous) {
        if (previous == null || previous.isBlank()) {
            CorrelationId.clear();
        } else {
            CorrelationId.set(previous);
        }
    }
}

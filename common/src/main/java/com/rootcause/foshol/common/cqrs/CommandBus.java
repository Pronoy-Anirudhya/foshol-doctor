package com.rootcause.foshol.common.cqrs;

import com.rootcause.foshol.common.util.CorrelationId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

@Slf4j
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
        String name = command.getClass().getSimpleName();
        String previous = CorrelationId.current();
        long started = System.nanoTime();
        try {
            CorrelationId.set(command.tracerId());
            log.info("command start {}", name);
            R result = handler.handle(command);
            log.info("command done {} ({} ms)", name, elapsedMs(started));
            return result;
        } catch (RuntimeException ex) {
            log.info("command failed {} ({} ms): {}", name, elapsedMs(started), ex.getClass().getSimpleName());
            throw ex;
        } finally {
            restoreMdc(previous);
        }
    }

    private static long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }

    private static void restoreMdc(String previous) {
        if (previous == null || previous.isBlank()) {
            CorrelationId.clear();
        } else {
            CorrelationId.set(previous);
        }
    }
}

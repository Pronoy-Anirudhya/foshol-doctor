package com.rootcause.foshol.common.cqrs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rootcause.foshol.common.CorrelationId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CommandBusTest {

    @AfterEach
    void clearMdc() {
        CorrelationId.clear();
    }

    @Test
    void handleDispatchesToRegisteredHandler() {
        CommandBus bus = new CommandBus();
        PingHandler handler = new PingHandler();
        bus.register(PingCommand.class, handler);
        String value = bus.handle(new PingCommand("ok"));
        assertThat(value).isEqualTo("ok");
    }

    @Test
    void duplicateRegistrationFails() {
        CommandBus bus = new CommandBus();
        bus.register(PingCommand.class, new PingHandler());
        assertThatThrownBy(() -> bus.register(PingCommand.class, new PingHandler()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(PingCommand.class.getName());
    }

    @Test
    void missingHandlerFails() {
        CommandBus bus = new CommandBus();
        assertThatThrownBy(() -> bus.handle(new PingCommand("x")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(PingCommand.class.getName());
    }

    @Test
    void restoresPreviousCorrelationId() {
        CorrelationId.set("before");
        CommandBus bus = new CommandBus();
        bus.register(PingCommand.class, new CommandHandler<PingCommand, String>() {
            @Override
            public Class<PingCommand> commandType() {
                return PingCommand.class;
            }

            @Override
            public String handle(PingCommand command) {
                assertThat(CorrelationId.current()).isEqualTo("during");
                return command.value();
            }
        });
        String value = bus.handle(new PingCommand("v"));
        assertThat(value).isEqualTo("v");
        assertThat(CorrelationId.current()).isEqualTo("before");
    }

    record PingCommand(String value) implements Command {
        @Override
        public String tracerId() {
            return "during";
        }
    }

    static final class PingHandler implements CommandHandler<PingCommand, String> {
        @Override
        public Class<PingCommand> commandType() {
            return PingCommand.class;
        }

        @Override
        public String handle(PingCommand command) {
            return command.value();
        }
    }
}

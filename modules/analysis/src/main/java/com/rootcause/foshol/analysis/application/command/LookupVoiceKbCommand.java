package com.rootcause.foshol.analysis.application.command;

import com.rootcause.foshol.common.cqrs.Command;
import java.util.UUID;

public record LookupVoiceKbCommand(UUID cropId, byte[] audio, String preferredLanguage, String correlationId)
        implements Command {

    @Override
    public String tracerId() {
        if (correlationId == null || correlationId.isBlank()) {
            return Command.super.tracerId();
        }
        return correlationId;
    }
}

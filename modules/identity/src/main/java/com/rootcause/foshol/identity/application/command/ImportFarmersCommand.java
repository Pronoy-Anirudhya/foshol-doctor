package com.rootcause.foshol.identity.application.command;

import com.rootcause.foshol.common.cqrs.Command;
import java.util.UUID;

public record ImportFarmersCommand(UUID officerId, byte[] csvBytes, String contentType) implements Command {}

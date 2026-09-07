package com.rootcause.foshol.identity.application.command;

import com.rootcause.foshol.common.cqrs.Command;

public record RequestOtpCommand(String phone) implements Command {}

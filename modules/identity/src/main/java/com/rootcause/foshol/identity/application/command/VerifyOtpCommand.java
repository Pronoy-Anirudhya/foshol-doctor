package com.rootcause.foshol.identity.application.command;

import com.rootcause.foshol.common.cqrs.Command;

public record VerifyOtpCommand(String phone, String code) implements Command {}

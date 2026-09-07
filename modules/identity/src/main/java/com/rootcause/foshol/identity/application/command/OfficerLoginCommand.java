package com.rootcause.foshol.identity.application.command;

import com.rootcause.foshol.common.cqrs.Command;

public record OfficerLoginCommand(String username, String password) implements Command {}

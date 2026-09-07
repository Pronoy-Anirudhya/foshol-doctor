package com.rootcause.foshol.common.cqrs;

public interface CommandHandler<C extends Command, R> {

    Class<C> commandType();

    R handle(C command);
}

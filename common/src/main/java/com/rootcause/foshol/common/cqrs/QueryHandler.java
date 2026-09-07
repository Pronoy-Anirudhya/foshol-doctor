package com.rootcause.foshol.common.cqrs;

public interface QueryHandler<Q extends Query, R> {

    Class<Q> queryType();

    R handle(Q query);
}

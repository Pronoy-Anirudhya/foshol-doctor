package com.rootcause.foshol.identity.application.query.handler;

import com.rootcause.foshol.common.cqrs.QueryHandler;
import com.rootcause.foshol.identity.application.query.DivisionResponse;
import com.rootcause.foshol.identity.application.query.GeoCataloguePort;
import com.rootcause.foshol.identity.application.query.ListDivisionsQuery;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListDivisionsQueryHandler implements QueryHandler<ListDivisionsQuery, List<DivisionResponse>> {

    @Override
    public Class<ListDivisionsQuery> queryType() {
        return ListDivisionsQuery.class;
    }

    private final GeoCataloguePort catalogue;

    public ListDivisionsQueryHandler(GeoCataloguePort catalogue) {
        this.catalogue = catalogue;
    }

    @Transactional(readOnly = true)
    @Override
    public List<DivisionResponse> handle(ListDivisionsQuery query) {
        return catalogue.listDivisions();
    }
}

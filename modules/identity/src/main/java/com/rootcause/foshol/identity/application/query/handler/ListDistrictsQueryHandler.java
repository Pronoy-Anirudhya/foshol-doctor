package com.rootcause.foshol.identity.application.query.handler;

import com.rootcause.foshol.common.contract.ErrorCodes;
import com.rootcause.foshol.common.cqrs.QueryHandler;
import com.rootcause.foshol.identity.application.query.DistrictResponse;
import com.rootcause.foshol.identity.application.port.GeoCataloguePort;
import com.rootcause.foshol.identity.application.query.ListDistrictsQuery;
import com.rootcause.foshol.identity.domain.IdentityException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListDistrictsQueryHandler implements QueryHandler<ListDistrictsQuery, List<DistrictResponse>> {

    @Override
    public Class<ListDistrictsQuery> queryType() {
        return ListDistrictsQuery.class;
    }

    private final GeoCataloguePort catalogue;

    public ListDistrictsQueryHandler(GeoCataloguePort catalogue) {
        this.catalogue = catalogue;
    }

    @Transactional(readOnly = true)
    @Override
    public List<DistrictResponse> handle(ListDistrictsQuery query) {
        return catalogue
                .listDistricts(query.divisionCode())
                .orElseThrow(() -> new IdentityException(ErrorCodes.ERR_SUBJECT_NOT_FOUND, 404, "Division was not found."));
    }
}

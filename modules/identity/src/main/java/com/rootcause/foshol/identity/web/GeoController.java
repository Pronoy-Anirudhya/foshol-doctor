package com.rootcause.foshol.identity.web;

import com.rootcause.foshol.common.cqrs.QueryBus;
import com.rootcause.foshol.identity.application.query.DistrictResponse;
import com.rootcause.foshol.identity.application.query.DivisionResponse;
import com.rootcause.foshol.identity.application.query.ListDistrictsQuery;
import com.rootcause.foshol.identity.application.query.ListDivisionsQuery;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/geo")
public class GeoController {

    private final QueryBus queries;

    public GeoController(QueryBus queries) {
        this.queries = queries;
    }

    @GetMapping("/divisions")
    public List<DivisionResponse> listDivisions() {
        return queries.handle(new ListDivisionsQuery());
    }

    @GetMapping("/divisions/{divisionCode}/districts")
    public List<DistrictResponse> listDistricts(@PathVariable String divisionCode) {
        return queries.handle(new ListDistrictsQuery(divisionCode));
    }
}

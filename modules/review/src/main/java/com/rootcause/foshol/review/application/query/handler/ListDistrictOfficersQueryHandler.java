package com.rootcause.foshol.review.application.query.handler;

import com.rootcause.foshol.common.Role;
import com.rootcause.foshol.identity.api.OfficerLookupApi;
import com.rootcause.foshol.identity.api.OfficerView;
import com.rootcause.foshol.review.application.query.ColleagueOfficerView;
import com.rootcause.foshol.review.application.query.ListDistrictOfficersQuery;
import com.rootcause.foshol.review.domain.ReviewException;
import com.rootcause.foshol.common.cqrs.QueryHandler;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListDistrictOfficersQueryHandler
        implements QueryHandler<ListDistrictOfficersQuery, List<ColleagueOfficerView>> {

    @Override
    public Class<ListDistrictOfficersQuery> queryType() {
        return ListDistrictOfficersQuery.class;
    }

    private final OfficerLookupApi officers;

    public ListDistrictOfficersQueryHandler(OfficerLookupApi officers) {
        this.officers = officers;
    }

    @Transactional(readOnly = true)
    @Override
    public List<ColleagueOfficerView> handle(ListDistrictOfficersQuery query) {
        OfficerView caller = officers.findById(query.callerId()).orElseThrow(ReviewException::taskNotFound);
        return officers.findActiveByDistrict(caller.districtCode()).stream()
                .filter(o -> Role.OFFICER.name().equals(o.role()))
                .filter(o -> !o.id().equals(query.callerId()))
                .map(o -> new ColleagueOfficerView(o.id(), o.name(), o.districtCode()))
                .toList();
    }
}

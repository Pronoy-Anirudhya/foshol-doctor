package com.rootcause.foshol.identity.application.query.handler;

import com.rootcause.foshol.common.contract.ErrorCodes;
import com.rootcause.foshol.common.cqrs.QueryHandler;
import com.rootcause.foshol.identity.application.port.FarmerReadPort;
import com.rootcause.foshol.identity.application.port.OfficerReadPort;
import com.rootcause.foshol.identity.application.port.OfficerReadSnapshot;
import com.rootcause.foshol.identity.application.query.FarmerRecord;
import com.rootcause.foshol.identity.application.query.FarmerRecordAssembler;
import com.rootcause.foshol.identity.application.query.GetFarmerQuery;
import com.rootcause.foshol.identity.domain.IdentityException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetFarmerQueryHandler implements QueryHandler<GetFarmerQuery, FarmerRecord> {

    private final FarmerReadPort farmers;
    private final OfficerReadPort officers;
    private final FarmerRecordAssembler assembler;

    public GetFarmerQueryHandler(
            FarmerReadPort farmers, OfficerReadPort officers, FarmerRecordAssembler assembler) {
        this.farmers = farmers;
        this.officers = officers;
        this.assembler = assembler;
    }

    @Override
    public Class<GetFarmerQuery> queryType() {
        return GetFarmerQuery.class;
    }

    @Transactional(readOnly = true)
    @Override
    public FarmerRecord handle(GetFarmerQuery query) {
        OfficerReadSnapshot officer = officers.findReadById(query.officerId())
                .orElseThrow(() -> new IdentityException(ErrorCodes.ERR_SUBJECT_NOT_FOUND, 404, "Subject not found."));
        return farmers.findByIdAndDistrictCode(query.farmerId(), officer.districtCode())
                .map(assembler::assemble)
                .orElseThrow(() -> new IdentityException(ErrorCodes.ERR_FARMER_NOT_FOUND, 404, "Farmer not found."));
    }
}

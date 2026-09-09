package com.rootcause.foshol.identity.application.query.handler;

import com.rootcause.foshol.common.ErrorCodes;
import com.rootcause.foshol.common.cqrs.QueryHandler;
import com.rootcause.foshol.identity.application.FarmerRecord;
import com.rootcause.foshol.identity.application.FarmerRecordAssembler;
import com.rootcause.foshol.identity.application.query.GetFarmerQuery;
import com.rootcause.foshol.identity.domain.IdentityException;
import com.rootcause.foshol.identity.infrastructure.FarmerJpaRepository;
import com.rootcause.foshol.identity.infrastructure.FieldOfficerJpaRepository;
import com.rootcause.foshol.identity.infrastructure.OfficerReadRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetFarmerQueryHandler implements QueryHandler<GetFarmerQuery, FarmerRecord> {

    private final FarmerJpaRepository farmers;
    private final FieldOfficerJpaRepository officers;
    private final FarmerRecordAssembler assembler;

    public GetFarmerQueryHandler(
            FarmerJpaRepository farmers, FieldOfficerJpaRepository officers, FarmerRecordAssembler assembler) {
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
        OfficerReadRow officer = officers.findReadById(query.officerId())
                .orElseThrow(() -> new IdentityException(ErrorCodes.ERR_SUBJECT_NOT_FOUND, 404, "Subject not found."));
        return farmers.findByIdAndDistrictCode(query.farmerId(), officer.getDistrictCode())
                .map(assembler::assemble)
                .orElseThrow(() -> new IdentityException(ErrorCodes.ERR_FARMER_NOT_FOUND, 404, "Farmer not found."));
    }
}

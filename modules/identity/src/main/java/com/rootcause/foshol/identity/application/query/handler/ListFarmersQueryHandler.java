package com.rootcause.foshol.identity.application.query.handler;

import com.rootcause.foshol.common.contract.ErrorCodes;
import com.rootcause.foshol.common.cqrs.QueryHandler;
import com.rootcause.foshol.identity.application.port.FarmerDirectoryPage;
import com.rootcause.foshol.identity.application.port.FarmerReadPort;
import com.rootcause.foshol.identity.application.port.OfficerReadPort;
import com.rootcause.foshol.identity.application.port.OfficerReadSnapshot;
import com.rootcause.foshol.identity.application.query.FarmerRecord;
import com.rootcause.foshol.identity.application.query.FarmerRecordAssembler;
import com.rootcause.foshol.identity.application.query.FarmerRecordPage;
import com.rootcause.foshol.identity.application.query.ListFarmersQuery;
import com.rootcause.foshol.identity.domain.IdentityException;
import com.rootcause.foshol.identity.domain.PhoneHash;
import com.rootcause.foshol.identity.domain.PhoneNumber;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListFarmersQueryHandler implements QueryHandler<ListFarmersQuery, FarmerRecordPage> {

    private final FarmerReadPort farmers;
    private final OfficerReadPort officers;
    private final FarmerRecordAssembler assembler;

    public ListFarmersQueryHandler(
            FarmerReadPort farmers, OfficerReadPort officers, FarmerRecordAssembler assembler) {
        this.farmers = farmers;
        this.officers = officers;
        this.assembler = assembler;
    }

    @Override
    public Class<ListFarmersQuery> queryType() {
        return ListFarmersQuery.class;
    }

    @Transactional(readOnly = true)
    @Override
    public FarmerRecordPage handle(ListFarmersQuery query) {
        OfficerReadSnapshot officer = officers.findReadById(query.officerId())
                .orElseThrow(() -> new IdentityException(ErrorCodes.ERR_SUBJECT_NOT_FOUND, 404, "Subject not found."));
        boolean hasQ = query.q() != null && !query.q().isBlank();
        boolean hasPhone = query.phone() != null && !query.phone().isBlank();
        if (hasQ && hasPhone) {
            throw new IdentityException(ErrorCodes.ERR_BAD_REQUEST, 400, "Provide either q or phone, not both.");
        }
        int page = Math.max(query.page(), 0);
        int size = query.size() < 1 ? 20 : Math.min(query.size(), 100);
        if (hasPhone) {
            String hash = PhoneHash.of(PhoneNumber.parse(query.phone())).hex();
            List<FarmerRecord> content = farmers.findByPhoneHashAndDistrictCode(hash, officer.districtCode())
                    .map(assembler::assemble)
                    .map(List::of)
                    .orElseGet(List::of);
            return new FarmerRecordPage(content, page, size, content.size(), content.isEmpty() ? 0 : 1);
        }
        FarmerDirectoryPage result;
        if (hasQ) {
            result = farmers.findByDistrictCodeAndName(officer.districtCode(), query.q().trim(), page, size);
        } else {
            result = farmers.findByDistrictCode(officer.districtCode(), page, size);
        }
        List<FarmerRecord> content = result.content().stream().map(assembler::assemble).toList();
        return new FarmerRecordPage(content, result.page(), result.size(), result.totalElements(), result.totalPages());
    }
}

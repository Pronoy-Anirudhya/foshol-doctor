package com.rootcause.foshol.identity.application.query.handler;

import com.rootcause.foshol.common.ErrorCodes;
import com.rootcause.foshol.common.cqrs.QueryHandler;
import com.rootcause.foshol.identity.application.FarmerRecord;
import com.rootcause.foshol.identity.application.FarmerRecordAssembler;
import com.rootcause.foshol.identity.application.query.FarmerRecordPage;
import com.rootcause.foshol.identity.application.query.ListFarmersQuery;
import com.rootcause.foshol.identity.domain.IdentityException;
import com.rootcause.foshol.identity.domain.PhoneHash;
import com.rootcause.foshol.identity.domain.PhoneNumber;
import com.rootcause.foshol.identity.infrastructure.FarmerDirectoryRow;
import com.rootcause.foshol.identity.infrastructure.FarmerJpaRepository;
import com.rootcause.foshol.identity.infrastructure.FieldOfficerJpaRepository;
import com.rootcause.foshol.identity.infrastructure.OfficerReadRow;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListFarmersQueryHandler implements QueryHandler<ListFarmersQuery, FarmerRecordPage> {

    private final FarmerJpaRepository farmers;
    private final FieldOfficerJpaRepository officers;
    private final FarmerRecordAssembler assembler;

    public ListFarmersQueryHandler(
            FarmerJpaRepository farmers, FieldOfficerJpaRepository officers, FarmerRecordAssembler assembler) {
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
        OfficerReadRow officer = officers.findReadById(query.officerId())
                .orElseThrow(() -> new IdentityException(ErrorCodes.ERR_SUBJECT_NOT_FOUND, 404, "Subject not found."));
        boolean hasQ = query.q() != null && !query.q().isBlank();
        boolean hasPhone = query.phone() != null && !query.phone().isBlank();
        if (hasQ && hasPhone) {
            throw new IdentityException(ErrorCodes.ERR_BAD_REQUEST, 400, "Provide either q or phone, not both.");
        }
        int page = Math.max(query.page(), 0);
        int size = query.size() < 1 ? 20 : Math.min(query.size(), 100);
        PageRequest pageable = PageRequest.of(page, size);
        if (hasPhone) {
            String hash = PhoneHash.of(PhoneNumber.parse(query.phone())).hex();
            List<FarmerRecord> content = farmers.findByPhoneHashAndDistrictCode(hash, officer.getDistrictCode())
                    .map(assembler::assemble)
                    .map(List::of)
                    .orElseGet(List::of);
            return new FarmerRecordPage(content, page, size, content.size(), content.isEmpty() ? 0 : 1);
        }
        Page<FarmerDirectoryRow> result;
        if (hasQ) {
            result = farmers.findByDistrictCodeAndNameContainingIgnoreCaseOrderByCreatedAtDesc(
                    officer.getDistrictCode(), query.q().trim(), pageable);
        } else {
            result = farmers.findByDistrictCodeOrderByCreatedAtDesc(officer.getDistrictCode(), pageable);
        }
        List<FarmerRecord> content = result.getContent().stream().map(assembler::assemble).toList();
        return new FarmerRecordPage(content, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }
}

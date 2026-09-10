package com.rootcause.foshol.intake.infrastructure.adapter;

import com.rootcause.foshol.common.enums.CropQuantityUnit;
import com.rootcause.foshol.common.enums.FieldAreaUnit;
import com.rootcause.foshol.common.enums.MetricsSource;
import com.rootcause.foshol.common.enums.Role;
import com.rootcause.foshol.common.cqrs.CommandBus;
import com.rootcause.foshol.intake.api.CaseIntakeApi;
import com.rootcause.foshol.intake.api.CaseSummary;
import com.rootcause.foshol.intake.application.query.StaffRegionAccess;
import com.rootcause.foshol.intake.application.command.RecordFieldMetricsCommand;
import com.rootcause.foshol.intake.application.command.RecordTranscriptCommand;
import com.rootcause.foshol.intake.application.port.CaseQueryPort;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CaseIntakeApiAdapter implements CaseIntakeApi {

    private final CaseQueryPort queries;
    private final CommandBus commands;
    private final StaffRegionAccess staffRegion;

    public CaseIntakeApiAdapter(CaseQueryPort queries, CommandBus commands, StaffRegionAccess staffRegion) {
        this.queries = queries;
        this.commands = commands;
        this.staffRegion = staffRegion;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CaseSummary> findById(UUID caseId) {
        return queries.findSummary(caseId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isOwnedBy(UUID caseId, UUID farmerId) {
        return queries.findFarmerId(caseId).filter(farmerId::equals).isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean officerSharesDistrict(UUID caseId, UUID officerId) {
        return staffRegion.allows(Role.OFFICER, officerId, caseId);
    }

    @Override
    public void recordTranscript(UUID caseId, String transcriptBn, BigDecimal asrConfidence) {
        commands.handle(new RecordTranscriptCommand(caseId, transcriptBn, asrConfidence));
    }

    @Override
    public void recordFieldMetrics(
            UUID caseId,
            BigDecimal fieldArea,
            FieldAreaUnit fieldAreaUnit,
            BigDecimal cropQuantity,
            CropQuantityUnit cropQuantityUnit,
            MetricsSource source) {
        commands.handle(new RecordFieldMetricsCommand(
                caseId, fieldArea, fieldAreaUnit, cropQuantity, cropQuantityUnit, source));
    }
}

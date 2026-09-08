package com.rootcause.foshol.intake.infrastructure;

import com.rootcause.foshol.common.CropQuantityUnit;
import com.rootcause.foshol.common.FieldAreaUnit;
import com.rootcause.foshol.common.MetricsSource;
import com.rootcause.foshol.common.cqrs.CommandBus;
import com.rootcause.foshol.intake.api.CaseIntakeApi;
import com.rootcause.foshol.intake.api.CaseSummary;
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

    public CaseIntakeApiAdapter(CaseQueryPort queries, CommandBus commands) {
        this.queries = queries;
        this.commands = commands;
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

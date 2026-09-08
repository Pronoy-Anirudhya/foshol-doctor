package com.rootcause.foshol.intake.application.command;

import com.rootcause.foshol.common.CropQuantityUnit;
import com.rootcause.foshol.common.FieldAreaUnit;
import com.rootcause.foshol.common.MetricsSource;
import com.rootcause.foshol.common.cqrs.Command;
import java.math.BigDecimal;
import java.util.UUID;

public record RecordFieldMetricsCommand(
        UUID caseId,
        BigDecimal fieldArea,
        FieldAreaUnit fieldAreaUnit,
        BigDecimal cropQuantity,
        CropQuantityUnit cropQuantityUnit,
        MetricsSource source) implements Command {}

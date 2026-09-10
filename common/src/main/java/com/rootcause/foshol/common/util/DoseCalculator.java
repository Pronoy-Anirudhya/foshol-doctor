package com.rootcause.foshol.common.util;

import com.rootcause.foshol.common.enums.FieldAreaUnit;
import com.rootcause.foshol.common.enums.RemedyRateBasis;
import com.rootcause.foshol.common.enums.RemedyRateUnit;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure dose suggestion from case field area and a remedy rate row. Never invents rates — callers
 * pass human-owned {@code rateAmount}/{@code rateBasis} or get an empty result.
 */
public final class DoseCalculator {

    private DoseCalculator() {}

    public static ComputedDose compute(
            BigDecimal fieldArea,
            FieldAreaUnit fieldAreaUnit,
            BigDecimal rateAmount,
            RemedyRateUnit rateUnit,
            RemedyRateBasis rateBasis) {
        if (rateAmount == null || rateUnit == null || rateBasis == null) {
            return null;
        }
        if (rateBasis == RemedyRateBasis.FIXED) {
            return new ComputedDose(
                    rateAmount.setScale(3, RoundingMode.HALF_UP),
                    rateUnit,
                    rateBasis,
                    fieldArea,
                    fieldAreaUnit);
        }
        if (fieldArea == null || fieldAreaUnit == null) {
            return null;
        }
        FieldAreaUnit basisUnit = basisToAreaUnit(rateBasis);
        if (basisUnit == null) {
            return null;
        }
        BigDecimal areaInBasis = FieldAreaConverter.convert(fieldArea, fieldAreaUnit, basisUnit);
        if (areaInBasis == null) {
            return null;
        }
        BigDecimal amount = areaInBasis.multiply(rateAmount).setScale(3, RoundingMode.HALF_UP);
        return new ComputedDose(amount, rateUnit, rateBasis, fieldArea, fieldAreaUnit);
    }

    private static FieldAreaUnit basisToAreaUnit(RemedyRateBasis basis) {
        return switch (basis) {
            case PER_DECIMAL -> FieldAreaUnit.DECIMAL;
            case PER_SQ_M -> FieldAreaUnit.SQ_M;
            case PER_HECTARE -> FieldAreaUnit.HECTARE;
            case PER_ACRE -> FieldAreaUnit.ACRE;
            case PER_SQ_FT -> FieldAreaUnit.SQ_FT;
            case FIXED -> null;
        };
    }

    public record ComputedDose(
            BigDecimal amount,
            RemedyRateUnit unit,
            RemedyRateBasis basis,
            BigDecimal fromArea,
            FieldAreaUnit fromAreaUnit) {}
}

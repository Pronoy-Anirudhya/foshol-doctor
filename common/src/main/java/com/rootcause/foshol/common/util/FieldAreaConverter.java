package com.rootcause.foshol.common.util;

import com.rootcause.foshol.common.enums.FieldAreaUnit;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Converts field area between units for dose calculation. Pure util — no Spring. */
public final class FieldAreaConverter {

    private static final BigDecimal SQ_M_PER_DECIMAL = new BigDecimal("40.4685642");
    private static final BigDecimal SQ_M_PER_SQ_FT = new BigDecimal("0.09290304");
    private static final BigDecimal SQ_M_PER_HECTARE = new BigDecimal("10000");
    private static final BigDecimal SQ_M_PER_ACRE = new BigDecimal("4046.8564224");

    private FieldAreaConverter() {}

    public static BigDecimal toSquareMetres(BigDecimal amount, FieldAreaUnit unit) {
        if (amount == null || unit == null) {
            return null;
        }
        return switch (unit) {
            case SQ_M -> amount;
            case SQ_FT -> amount.multiply(SQ_M_PER_SQ_FT);
            case DECIMAL -> amount.multiply(SQ_M_PER_DECIMAL);
            case HECTARE -> amount.multiply(SQ_M_PER_HECTARE);
            case ACRE -> amount.multiply(SQ_M_PER_ACRE);
        };
    }

    public static BigDecimal fromSquareMetres(BigDecimal sqM, FieldAreaUnit target) {
        if (sqM == null || target == null) {
            return null;
        }
        BigDecimal divisor =
                switch (target) {
                    case SQ_M -> BigDecimal.ONE;
                    case SQ_FT -> SQ_M_PER_SQ_FT;
                    case DECIMAL -> SQ_M_PER_DECIMAL;
                    case HECTARE -> SQ_M_PER_HECTARE;
                    case ACRE -> SQ_M_PER_ACRE;
                };
        return sqM.divide(divisor, 6, RoundingMode.HALF_UP);
    }

    public static BigDecimal convert(BigDecimal amount, FieldAreaUnit from, FieldAreaUnit to) {
        return fromSquareMetres(toSquareMetres(amount, from), to);
    }
}

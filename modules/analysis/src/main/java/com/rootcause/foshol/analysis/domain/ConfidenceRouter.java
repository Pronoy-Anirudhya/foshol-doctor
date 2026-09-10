package com.rootcause.foshol.analysis.domain;

import com.rootcause.foshol.analysis.domain.spec.PrimaryPathSpec;
import com.rootcause.foshol.analysis.domain.spec.SecondaryPathSpec;
import com.rootcause.foshol.common.enums.DecisionPath;
import com.rootcause.foshol.common.contract.ErrorCodes;
import java.math.BigDecimal;

public final class ConfidenceRouter {

    private ConfidenceRouter() {}

    public static RoutingDecision route(
            BigDecimal top1,
            BigDecimal top2,
            boolean prescribable,
            boolean kbInconclusive,
            BigDecimal high,
            BigDecimal low) {
        BigDecimal margin = marginOf(top1, top2);
        if (PrimaryPathSpec.isSatisfied(top1, prescribable, high)) {
            return new RoutingDecision(DecisionPath.PRIMARY, top1, top2, margin, null);
        }
        if (top1 != null && top1.compareTo(high) >= 0 && !prescribable) {
            return new RoutingDecision(
                    DecisionPath.UNDETERMINED, top1, top2, margin, ErrorCodes.ERR_NO_REMEDY_FOR_DIAGNOSIS);
        }
        if (SecondaryPathSpec.isSatisfied(top1, kbInconclusive, high, low)) {
            return new RoutingDecision(DecisionPath.SECONDARY, top1, top2, margin, null);
        }
        return new RoutingDecision(DecisionPath.UNDETERMINED, top1, top2, margin, null);
    }

    private static BigDecimal marginOf(BigDecimal top1, BigDecimal top2) {
        if (top1 == null || top2 == null) {
            return null;
        }
        return top1.subtract(top2);
    }
}

package com.rootcause.foshol.analysis.domain;

import com.rootcause.foshol.common.enums.DecisionPath;
import java.math.BigDecimal;

public record RoutingDecision(
        DecisionPath path, BigDecimal top1, BigDecimal top2, BigDecimal margin, String errorCode) {}

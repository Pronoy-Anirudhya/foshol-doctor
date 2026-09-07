package com.rootcause.foshol.analysis.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.rootcause.foshol.common.DecisionPath;
import com.rootcause.foshol.common.ErrorCodes;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ConfidenceRouterTest {

    private static final BigDecimal HIGH = new BigDecimal("0.75");
    private static final BigDecimal LOW = new BigDecimal("0.45");

    @Test
    void primaryWhenHighAndPrescribable() {
        assertThat(ConfidenceRouter.route(new BigDecimal("0.76"), new BigDecimal("0.10"), true, false, HIGH, LOW).path())
                .isEqualTo(DecisionPath.PRIMARY);
        assertThat(ConfidenceRouter.route(new BigDecimal("0.75"), new BigDecimal("0.10"), true, false, HIGH, LOW).path())
                .isEqualTo(DecisionPath.PRIMARY);
        assertThat(ConfidenceRouter.route(new BigDecimal("0.7499"), new BigDecimal("0.10"), true, false, HIGH, LOW).path())
                .isEqualTo(DecisionPath.SECONDARY);
    }

    @Test
    void marginDoesNotDemotePrimary() {
        RoutingDecision decision =
                ConfidenceRouter.route(new BigDecimal("0.80"), new BigDecimal("0.79"), true, false, HIGH, LOW);
        assertThat(decision.path()).isEqualTo(DecisionPath.PRIMARY);
        assertThat(decision.margin()).isEqualByComparingTo("0.01");
    }

    @Test
    void undeterminedWhenHighWithoutRemedy() {
        RoutingDecision decision =
                ConfidenceRouter.route(new BigDecimal("0.90"), new BigDecimal("0.05"), false, false, HIGH, LOW);
        assertThat(decision.path()).isEqualTo(DecisionPath.UNDETERMINED);
        assertThat(decision.errorCode()).isEqualTo(ErrorCodes.ERR_NO_REMEDY_FOR_DIAGNOSIS);
    }

    @Test
    void secondaryWhenMidAndKbConclusive() {
        assertThat(ConfidenceRouter.route(new BigDecimal("0.60"), new BigDecimal("0.20"), true, false, HIGH, LOW).path())
                .isEqualTo(DecisionPath.SECONDARY);
        assertThat(ConfidenceRouter.route(new BigDecimal("0.45"), new BigDecimal("0.20"), true, false, HIGH, LOW).path())
                .isEqualTo(DecisionPath.SECONDARY);
    }

    @Test
    void undeterminedWhenMidAndKbInconclusive() {
        assertThat(ConfidenceRouter.route(new BigDecimal("0.60"), new BigDecimal("0.20"), true, true, HIGH, LOW).path())
                .isEqualTo(DecisionPath.UNDETERMINED);
    }

    @Test
    void undeterminedBelowLow() {
        assertThat(ConfidenceRouter.route(new BigDecimal("0.44"), new BigDecimal("0.10"), true, false, HIGH, LOW).path())
                .isEqualTo(DecisionPath.UNDETERMINED);
    }

    @Test
    void undeterminedWhenAllUnmapped() {
        assertThat(ConfidenceRouter.route(null, null, true, false, HIGH, LOW).path())
                .isEqualTo(DecisionPath.UNDETERMINED);
    }

    @Test
    void undeterminedJustBelowLowBoundary() {
        assertThat(ConfidenceRouter.route(new BigDecimal("0.4499"), new BigDecimal("0.10"), true, false, HIGH, LOW).path())
                .isEqualTo(DecisionPath.UNDETERMINED);
    }
}

package com.rootcause.foshol.intake.domain.spec;

import static org.assertj.core.api.Assertions.assertThat;

import com.rootcause.foshol.common.CaseStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TransitionAllowedSpecTest {

    @ParameterizedTest
    @CsvSource({
        "SUBMITTED,ANALYSING,true",
        "ANALYSING,ANALYSED,true",
        "ANALYSED,IN_REVIEW,true",
        "ANALYSING,FAILED,true",
        "IN_REVIEW,ADVISED,true",
        "IN_REVIEW,REJECTED,true",
        "FAILED,ADVISED,true",
        "FAILED,REJECTED,true",
        "SUBMITTED,ADVISED,false",
        "ADVISED,REJECTED,false",
        "REJECTED,ADVISED,false",
        "ADVISED,SUBMITTED,false"
    })
    void matrix(CaseStatus from, CaseStatus to, boolean allowed) {
        assertThat(TransitionAllowedSpec.INSTANCE.isSatisfied(from, to)).isEqualTo(allowed);
    }

    @Test
    void nullsAreRejected() {
        assertThat(TransitionAllowedSpec.INSTANCE.isSatisfied(null, CaseStatus.ANALYSING)).isFalse();
    }
}

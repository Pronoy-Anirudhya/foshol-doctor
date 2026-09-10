package com.rootcause.foshol.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.rootcause.foshol.common.enums.CandidateSource;
import com.rootcause.foshol.common.events.CandidateView;
import com.rootcause.foshol.common.events.SymptomView;
import com.rootcause.foshol.common.enums.SymptomSource;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogueLocaleTest {

    @Test
    void candidateFallsBackWhenEnglishBlank() {
        UUID id = UUID.fromString("01800000-0000-7000-8000-000000000101");
        CandidateView view = CandidateView.of(
                id, "blast", "ব্লাস্ট", "  ", BigDecimal.ONE, 1, CandidateSource.MODEL);
        assertThat(view.diseaseNameEn()).isEqualTo("ব্লাস্ট");
        assertThat(view.diseaseNameEnFallback()).isTrue();
    }

    @Test
    void symptomKeepsEnglishWhenPresent() {
        UUID id = UUID.fromString("01800000-0000-7000-8000-000000000201");
        SymptomView view = SymptomView.of(
                id, "leaf_spot", "দাগ", "Leaf spot", BigDecimal.ONE, SymptomSource.SPEECH, "VECTOR");
        assertThat(view.nameEn()).isEqualTo("Leaf spot");
        assertThat(view.nameEnFallback()).isFalse();
    }
}

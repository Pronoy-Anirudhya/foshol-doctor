package com.rootcause.foshol.review.application.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.rootcause.foshol.common.enums.AdvisoryAction;
import com.rootcause.foshol.common.enums.RemedyType;
import com.rootcause.foshol.common.enums.Severity;
import com.rootcause.foshol.common.util.Uuid7;
import com.rootcause.foshol.identity.api.OfficerLookupApi;
import com.rootcause.foshol.identity.api.OfficerView;
import com.rootcause.foshol.knowledge.api.DiseaseView;
import com.rootcause.foshol.knowledge.api.KnowledgeQueryApi;
import com.rootcause.foshol.knowledge.api.RemedyView;
import com.rootcause.foshol.review.ReviewFixtures;
import com.rootcause.foshol.review.api.AdvisoryView;
import com.rootcause.foshol.review.api.RemedyRefView;
import com.rootcause.foshol.review.domain.Advisory;
import com.rootcause.foshol.review.domain.AdvisoryRemedy;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdvisoryViewMapperTest {

    @Mock
    private KnowledgeQueryApi knowledge;

    @Mock
    private OfficerLookupApi officers;

    @Test
    void copiesEnglishAndFlagsFallbackWhenMissing() {
        UUID advisoryId = Uuid7.create();
        UUID caseId = Uuid7.create();
        RemedyView remedy = new RemedyView(
                ReviewFixtures.REMEDY_R1,
                ReviewFixtures.DISEASE_D,
                RemedyType.CULTURAL,
                "শিরোনাম",
                List.of("ধাপ"),
                "মাত্রা",
                null,
                "LOW",
                "LOW",
                "src",
                null,
                null,
                null,
                "নোট",
                null,
                List.of(),
                null,
                null);
        when(knowledge.findDiseaseById(ReviewFixtures.DISEASE_D))
                .thenReturn(Optional.of(new DiseaseView(
                        ReviewFixtures.DISEASE_D,
                        ReviewFixtures.CROP,
                        "brown_spot",
                        "ব্লাস্ট",
                        "  ",
                        null,
                        Severity.LOW,
                        false)));
        when(knowledge.listActiveRemedies(ReviewFixtures.DISEASE_D)).thenReturn(List.of(remedy));
        when(officers.findById(ReviewFixtures.OFFICER_A))
                .thenReturn(Optional.of(new OfficerView(
                        ReviewFixtures.OFFICER_A, "Officer A", "DHK01", "OFFICER", true, "DHK")));
        Advisory advisory = Advisory.firstVersion(
                advisoryId,
                caseId,
                ReviewFixtures.DISEASE_D,
                ReviewFixtures.OFFICER_A,
                AdvisoryAction.APPROVED,
                null,
                List.of(new AdvisoryRemedy(ReviewFixtures.REMEDY_R1, (short) 1)),
                ReviewFixtures.T0);

        AdvisoryView view = AdvisoryViewMapper.toView(advisory, knowledge, officers);

        assertThat(view.diseaseNameBn()).isEqualTo("ব্লাস্ট");
        assertThat(view.diseaseNameEn()).isEqualTo("ব্লাস্ট");
        assertThat(view.diseaseNameEnFallback()).isTrue();
        assertThat(view.remedies()).hasSize(1);
        RemedyRefView ref = view.remedies().getFirst();
        assertThat(ref.titleEn()).isEqualTo("শিরোনাম");
        assertThat(ref.titleEnFallback()).isTrue();
        assertThat(ref.stepsEn()).containsExactly("ধাপ");
        assertThat(ref.stepsEnFallback()).isTrue();
        assertThat(ref.dosageEn()).isEqualTo("মাত্রা");
        assertThat(ref.dosageEnFallback()).isTrue();
        assertThat(ref.rateNotesEn()).isEqualTo("নোট");
        assertThat(ref.rateNotesEnFallback()).isTrue();
    }
}

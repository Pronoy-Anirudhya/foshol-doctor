package com.rootcause.foshol.knowledge.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.rootcause.foshol.common.enums.RemedyType;
import com.rootcause.foshol.common.enums.Severity;
import com.rootcause.foshol.knowledge.application.query.CropReadModel;
import com.rootcause.foshol.knowledge.application.query.DiseaseReadModel;
import com.rootcause.foshol.knowledge.application.query.RemedyReadModel;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class KnowledgeWebMapperTest {

    private static final UUID RICE = UUID.fromString("01800000-0000-7000-8000-000000000001");
    private static final UUID BLAST = UUID.fromString("01800000-0000-7000-8000-000000000103");
    private static final UUID REMEDY = UUID.fromString("01800000-0000-7000-8000-000000000501");

    @Test
    void fallsBackToBanglaWhenEnglishMissing() {
        CropReadModel row = new CropReadModel(RICE, "rice", "ধান", null, "crop-rice", 1);
        CropResponse response = KnowledgeWebMapper.toCropResponse(row);
        assertThat(response.nameEn()).isEqualTo("ধান");
        assertThat(response.nameEnFallback()).isTrue();
    }

    @Test
    void fallbackFlagIsFalseWhenEnglishPresent() {
        CropReadModel row = new CropReadModel(RICE, "rice", "ধান", "Rice", "crop-rice", 1);
        CropResponse response = KnowledgeWebMapper.toCropResponse(row);
        assertThat(response.nameEn()).isEqualTo("Rice");
        assertThat(response.nameEnFallback()).isFalse();
    }

    @Test
    void blankEnglishIsTreatedAsMissing() {
        DiseaseReadModel row = new DiseaseReadModel(
                BLAST, RICE, "blast", "ব্লাস্ট", "  ", "বর্ণনা", null, Severity.LOW, false);
        DiseaseResponse response = KnowledgeWebMapper.toDiseaseResponse(row);
        assertThat(response.nameEn()).isEqualTo("ব্লাস্ট");
        assertThat(response.nameEnFallback()).isTrue();
        assertThat(response.descriptionEn()).isEqualTo("বর্ণনা");
        assertThat(response.descriptionEnFallback()).isTrue();
        assertThat(response.healthy()).isFalse();
        assertThat(response.cropId()).isEqualTo(RICE);
    }

    @Test
    void diseaseEnglishPresentDoesNotFallBack() {
        DiseaseReadModel row = new DiseaseReadModel(
                BLAST, RICE, "blast", "ব্লাস্ট", "Blast", "বর্ণনা", "Description", Severity.HIGH, false);
        DiseaseResponse response = KnowledgeWebMapper.toDiseaseResponse(row);
        assertThat(response.nameEn()).isEqualTo("Blast");
        assertThat(response.nameEnFallback()).isFalse();
        assertThat(response.descriptionEn()).isEqualTo("Description");
        assertThat(response.descriptionEnFallback()).isFalse();
    }

    @Test
    void remedyFallsBackToBanglaWhenEnglishMissing() {
        RemedyReadModel row = new RemedyReadModel(
                REMEDY,
                BLAST,
                RemedyType.CULTURAL,
                "শিরোনাম",
                List.of("ধাপ"),
                "মাত্রা",
                null,
                "LOW",
                "LOW",
                "src",
                1,
                null,
                null,
                null,
                "নোট",
                null,
                null,
                null,
                null);
        RemedyResponse response = KnowledgeWebMapper.toRemedyResponse(row);
        assertThat(response.titleEn()).isEqualTo("শিরোনাম");
        assertThat(response.titleEnFallback()).isTrue();
        assertThat(response.stepsEn()).containsExactly("ধাপ");
        assertThat(response.stepsEnFallback()).isTrue();
        assertThat(response.dosageEn()).isEqualTo("মাত্রা");
        assertThat(response.dosageEnFallback()).isTrue();
        assertThat(response.rateNotesEn()).isEqualTo("নোট");
        assertThat(response.rateNotesEnFallback()).isTrue();
    }

    @Test
    void remedyEnglishPresentDoesNotFallBack() {
        RemedyReadModel row = new RemedyReadModel(
                REMEDY,
                BLAST,
                RemedyType.CULTURAL,
                "শিরোনাম",
                List.of("ধাপ"),
                "মাত্রা",
                null,
                "LOW",
                "LOW",
                "src",
                1,
                null,
                null,
                null,
                "নোট",
                "Title",
                List.of("Step"),
                "Dose",
                "Note");
        RemedyResponse response = KnowledgeWebMapper.toRemedyResponse(row);
        assertThat(response.titleEn()).isEqualTo("Title");
        assertThat(response.titleEnFallback()).isFalse();
        assertThat(response.stepsEn()).containsExactly("Step");
        assertThat(response.stepsEnFallback()).isFalse();
        assertThat(response.dosageEn()).isEqualTo("Dose");
        assertThat(response.dosageEnFallback()).isFalse();
        assertThat(response.rateNotesEn()).isEqualTo("Note");
        assertThat(response.rateNotesEnFallback()).isFalse();
    }
}

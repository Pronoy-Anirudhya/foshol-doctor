package com.rootcause.foshol.knowledge.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.rootcause.foshol.common.Severity;
import com.rootcause.foshol.knowledge.application.query.CropReadModel;
import com.rootcause.foshol.knowledge.application.query.DiseaseReadModel;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class KnowledgeWebMapperTest {

    private static final UUID RICE = UUID.fromString("01800000-0000-7000-8000-000000000001");

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
                UUID.fromString("01800000-0000-7000-8000-000000000103"),
                RICE,
                "blast",
                "ব্লাস্ট",
                "  ",
                null,
                Severity.LOW,
                false);
        DiseaseResponse response = KnowledgeWebMapper.toDiseaseResponse(row);
        assertThat(response.nameEn()).isEqualTo("ব্লাস্ট");
        assertThat(response.nameEnFallback()).isTrue();
        assertThat(response.healthy()).isFalse();
        assertThat(response.cropId()).isEqualTo(RICE);
    }
}

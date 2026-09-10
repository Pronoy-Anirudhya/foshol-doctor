package com.rootcause.foshol.intake.application.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.rootcause.foshol.common.enums.Severity;
import com.rootcause.foshol.common.util.Uuid7;
import com.rootcause.foshol.knowledge.api.CropView;
import com.rootcause.foshol.knowledge.api.DiseaseView;
import org.junit.jupiter.api.Test;

class CatalogueNamesTest {

    @Test
    void cropUsesEnglishWhenPresent() {
        CropView crop = new CropView(Uuid7.create(), "rice", "ধান", "Rice", null);
        CatalogueNames names = CatalogueNames.crop(crop, "ধান");
        assertThat(names.bn()).isEqualTo("ধান");
        assertThat(names.en()).isEqualTo("Rice");
        assertThat(names.fallback()).isFalse();
    }

    @Test
    void cropFallsBackWhenEnglishBlank() {
        CropView crop = new CropView(Uuid7.create(), "rice", "ধান", " ", null);
        CatalogueNames names = CatalogueNames.crop(crop, "ধান");
        assertThat(names.en()).isEqualTo("ধান");
        assertThat(names.fallback()).isTrue();
    }

    @Test
    void diseaseFallsBackFromStoredBanglaWhenAdvisoryMissing() {
        CatalogueNames names = CatalogueNames.disease(null, "ব্লাস্ট");
        assertThat(names.bn()).isEqualTo("ব্লাস্ট");
        assertThat(names.en()).isEqualTo("ব্লাস্ট");
        assertThat(names.fallback()).isTrue();
    }

    @Test
    void diseaseUsesCatalogueEnglish() {
        DiseaseView disease = new DiseaseView(
                Uuid7.create(), Uuid7.create(), "blast", "ব্লাস্ট", "Blast", null, Severity.HIGH, false);
        CatalogueNames names = CatalogueNames.disease(disease, "ব্লাস্ট");
        assertThat(names.en()).isEqualTo("Blast");
        assertThat(names.fallback()).isFalse();
    }
}

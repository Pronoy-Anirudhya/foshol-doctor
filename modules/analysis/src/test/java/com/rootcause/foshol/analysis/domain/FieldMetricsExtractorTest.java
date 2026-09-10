package com.rootcause.foshol.analysis.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.rootcause.foshol.common.enums.CropQuantityUnit;
import com.rootcause.foshol.common.enums.FieldAreaUnit;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class FieldMetricsExtractorTest {

    @Test
    void extractsBanglaDecimalArea() {
        FieldMetricsExtractor.Extracted extracted =
                FieldMetricsExtractor.extract("জমি ২.৫ শতক আক্রান্ত");
        assertThat(extracted.fieldArea()).isEqualByComparingTo(new BigDecimal("2.5"));
        assertThat(extracted.fieldAreaUnit()).isEqualTo(FieldAreaUnit.DECIMAL);
    }

    @Test
    void extractsQuantityKg() {
        FieldMetricsExtractor.Extracted extracted =
                FieldMetricsExtractor.extract("ফসল প্রায় 40 কেজি নষ্ট হয়েছে, জমি 1 একর");
        assertThat(extracted.cropQuantity()).isEqualByComparingTo("40");
        assertThat(extracted.cropQuantityUnit()).isEqualTo(CropQuantityUnit.KG);
        assertThat(extracted.fieldArea()).isEqualByComparingTo("1");
        assertThat(extracted.fieldAreaUnit()).isEqualTo(FieldAreaUnit.ACRE);
    }

    @Test
    void emptyTranscript() {
        assertThat(FieldMetricsExtractor.extract(null).isEmpty()).isTrue();
        assertThat(FieldMetricsExtractor.extract("  ").isEmpty()).isTrue();
        assertThat(FieldMetricsExtractor.extract("পাতায় দাগ আছে").isEmpty()).isTrue();
    }
}

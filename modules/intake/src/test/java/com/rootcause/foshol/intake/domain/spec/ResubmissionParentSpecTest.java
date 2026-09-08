package com.rootcause.foshol.intake.domain.spec;

import static org.assertj.core.api.Assertions.assertThat;

import com.rootcause.foshol.common.CaseStatus;
import com.rootcause.foshol.common.FieldAreaUnit;
import com.rootcause.foshol.common.MetricsSource;
import com.rootcause.foshol.intake.domain.CaseImage;
import com.rootcause.foshol.intake.domain.DiagnosisCase;
import com.rootcause.foshol.intake.domain.vo.CaseId;
import com.rootcause.foshol.intake.domain.vo.ImageId;
import com.rootcause.foshol.intake.domain.vo.ImageQuality;
import com.rootcause.foshol.intake.domain.vo.ObjectKey;
import com.rootcause.foshol.intake.domain.vo.Sha256;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ResubmissionParentSpecTest {

    @Test
    void requiresSameFarmerAndRejectedStatus() {
        UUID farmer = UUID.fromString("01800000-0000-7000-8000-000000000201");
        DiagnosisCase rejected = caseWith(farmer, CaseStatus.REJECTED);
        assertThat(ResubmissionParentSpec.INSTANCE.isSatisfied(rejected, farmer)).isTrue();
        assertThat(ResubmissionParentSpec.INSTANCE.isSatisfied(rejected, UUID.randomUUID())).isFalse();
        assertThat(ResubmissionParentSpec.INSTANCE.isSatisfied(caseWith(farmer, CaseStatus.ADVISED), farmer))
                .isFalse();
        assertThat(ResubmissionParentSpec.INSTANCE.isSatisfied(null, farmer)).isFalse();
    }

    private static DiagnosisCase caseWith(UUID farmer, CaseStatus status) {
        CaseImage image = new CaseImage(
                ImageId.newId(),
                new ObjectKey("cases/x/img/y.jpg"),
                new ObjectKey("cases/x/img/y.jpg"),
                "image/jpeg",
                10,
                new ImageQuality(100, 0.5, new BigDecimal("0.500"), 320, 320),
                Sha256.ofUtf8("img"),
                true,
                1);
        return DiagnosisCase.rehydrate(
                CaseId.newId(),
                farmer,
                UUID.fromString("01800000-0000-7000-8000-000000000001"),
                null,
                status,
                null,
                null,
                "DHA",
                "corr",
                BigDecimal.ONE,
                FieldAreaUnit.DECIMAL,
                null,
                null,
                MetricsSource.FORM,
                0,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"),
                List.of(image),
                null);
    }
}

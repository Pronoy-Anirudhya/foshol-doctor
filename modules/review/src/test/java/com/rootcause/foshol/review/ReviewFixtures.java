package com.rootcause.foshol.review;

import com.rootcause.foshol.analysis.api.AnalysisView;
import com.rootcause.foshol.common.enums.AiMode;
import com.rootcause.foshol.common.enums.CandidateSource;
import com.rootcause.foshol.common.enums.DecisionPath;
import com.rootcause.foshol.common.util.Uuid7;
import com.rootcause.foshol.common.events.AnalysisCompleted;
import com.rootcause.foshol.common.events.AnalysisFailed;
import com.rootcause.foshol.common.events.CandidateView;
import com.rootcause.foshol.identity.api.FarmerView;
import com.rootcause.foshol.identity.api.OfficerView;
import com.rootcause.foshol.knowledge.api.DiseaseView;
import com.rootcause.foshol.knowledge.api.RemedyView;
import com.rootcause.foshol.common.enums.RemedyType;
import com.rootcause.foshol.common.enums.Severity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ReviewFixtures {

    public static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    public static final UUID FARMER = Uuid7.create();
    public static final UUID CROP = UUID.fromString("01800000-0000-7000-8000-000000000001");
    public static final UUID DISEASE_D = UUID.fromString("01800000-0000-7000-8000-000000000101");
    public static final UUID DISEASE_E = UUID.fromString("01800000-0000-7000-8000-000000000102");
    public static final UUID DISEASE_HEALTHY = UUID.fromString("01800000-0000-7000-8000-000000000106");
    public static final UUID REMEDY_R1 = Uuid7.create();
    public static final UUID REMEDY_R2 = Uuid7.create();
    public static final UUID REMEDY_R3 = Uuid7.create();
    public static final UUID REMEDY_NO_PHI = Uuid7.create();
    public static final UUID OFFICER_A = Uuid7.create();
    public static final UUID OFFICER_B = Uuid7.create();

    private ReviewFixtures() {}

    public static AnalysisCompleted analysisCompleted(DecisionPath path, BigDecimal top1) {
        UUID caseId = Uuid7.create();
        return new AnalysisCompleted(
                caseId,
                FARMER,
                CROP,
                path,
                AiMode.REPLAY,
                top1,
                new BigDecimal("0.10"),
                List.of(new CandidateView(DISEASE_D, "brown_spot", "d", "d", false, top1, 1, CandidateSource.MODEL)),
                List.of(),
                false,
                1,
                "corr",
                T0);
    }

    public static AnalysisFailed analysisFailed() {
        return new AnalysisFailed(Uuid7.create(), FARMER, "ERR_SIDECAR_UNAVAILABLE", "corr", T0);
    }

    public static OfficerView officer() {
        return new OfficerView(OFFICER_A, "Officer A", "DHK01", "OFFICER", true, "DHK");
    }

    public static OfficerView secondOfficer() {
        return new OfficerView(OFFICER_B, "Officer B", "DHK01", "OFFICER", true, "DHK");
    }

    public static FarmerView farmer() {
        return new FarmerView(FARMER, "Farmer A", "DHK01", "bn", "DHK");
    }

    public static DiseaseView diseaseD() {
        return new DiseaseView(DISEASE_D, CROP, "brown_spot", "d", "d", null, Severity.LOW, false);
    }

    public static DiseaseView diseaseE() {
        return new DiseaseView(DISEASE_E, CROP, "leaf_scald", "e", "e", null, Severity.LOW, false);
    }

    public static DiseaseView healthy() {
        return new DiseaseView(DISEASE_HEALTHY, CROP, "healthy", "h", "h", null, Severity.NONE, true);
    }

    public static RemedyView r1() {
        return new RemedyView(REMEDY_R1, DISEASE_D, RemedyType.CULTURAL, "", List.of(), null, null, "LOW", "LOW", "", null, null, null, null, null, null, null, null);
    }

    public static RemedyView r2() {
        return new RemedyView(REMEDY_R2, DISEASE_D, RemedyType.CHEMICAL, "", List.of(), null, 14, "LOW", "LOW", "", null, null, null, null, null, null, null, null);
    }

    public static RemedyView r3() {
        return new RemedyView(REMEDY_R3, DISEASE_E, RemedyType.CULTURAL, "", List.of(), null, null, "LOW", "LOW", "", null, null, null, null, null, null, null, null);
    }

    public static RemedyView chemicalWithoutPhi() {
        return new RemedyView(REMEDY_NO_PHI, DISEASE_D, RemedyType.CHEMICAL, "", List.of(), null, null, "LOW", "LOW", "", null, null, null, null, null, null, null, null);
    }

    public static AnalysisView analysisView(UUID caseId) {
        return new AnalysisView(
                caseId,
                DecisionPath.PRIMARY,
                AiMode.REPLAY,
                new BigDecimal("0.91"),
                new BigDecimal("0.40"),
                new BigDecimal("0.51"),
                List.of(new CandidateView(DISEASE_D, "brown_spot", "d", "d", false, new BigDecimal("0.91"), 1, CandidateSource.MODEL)),
                List.of(),
                null,
                null,
                null,
                List.of(),
                "model",
                "rev",
                10,
                null);
    }
}

package com.rootcause.foshol.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.rootcause.foshol.analysis.api.AnalysisApi;
import com.rootcause.foshol.analysis.api.AnalysisView;
import com.rootcause.foshol.common.AiMode;
import com.rootcause.foshol.common.CandidateSource;
import com.rootcause.foshol.common.CropQuantityUnit;
import com.rootcause.foshol.common.DecisionPath;
import com.rootcause.foshol.common.FieldAreaUnit;
import com.rootcause.foshol.common.MetricsSource;
import com.rootcause.foshol.common.ReviewState;
import com.rootcause.foshol.common.Uuid7;
import com.rootcause.foshol.common.events.AnalysisCompleted;
import com.rootcause.foshol.common.events.AnalysisFailed;
import com.rootcause.foshol.common.events.CandidateView;
import com.rootcause.foshol.identity.api.FarmerLookupApi;
import com.rootcause.foshol.identity.api.FarmerView;
import com.rootcause.foshol.identity.api.OfficerLookupApi;
import com.rootcause.foshol.identity.api.OfficerView;
import com.rootcause.foshol.intake.api.CaseIntakeApi;
import com.rootcause.foshol.intake.api.CaseSummary;
import com.rootcause.foshol.knowledge.api.CropView;
import com.rootcause.foshol.knowledge.api.DiseaseView;
import com.rootcause.foshol.knowledge.api.KnowledgeQueryApi;
import com.rootcause.foshol.knowledge.api.RemedyView;
import com.rootcause.foshol.knowledge.api.SymptomRefView;
import com.rootcause.foshol.review.application.command.ApproveCaseCommand;
import com.rootcause.foshol.review.application.command.handler.ApproveCaseCommandHandler;
import com.rootcause.foshol.review.application.command.ClaimReviewTaskCommand;
import com.rootcause.foshol.review.application.command.handler.ClaimReviewTaskCommandHandler;
import com.rootcause.foshol.review.application.command.RejectCaseCommand;
import com.rootcause.foshol.review.application.command.handler.RejectCaseCommandHandler;
import com.rootcause.foshol.review.application.command.ReviseAdvisoryCommand;
import com.rootcause.foshol.review.application.command.handler.ReviseAdvisoryCommandHandler;
import com.rootcause.foshol.review.application.command.handler.SweepExpiredClaimsCommandHandler;
import com.rootcause.foshol.review.application.port.ReviewTaskRepository;
import com.rootcause.foshol.review.application.query.AdminStatsQuery;
import com.rootcause.foshol.review.application.query.handler.AdminStatsQueryHandler;
import com.rootcause.foshol.review.application.query.OfficerQueueQuery;
import com.rootcause.foshol.review.application.query.handler.OfficerQueueQueryHandler;
import com.rootcause.foshol.review.application.query.OfficerQueueRow;
import com.rootcause.foshol.review.domain.ReviewException;
import com.rootcause.foshol.review.infrastructure.CreateReviewTaskOnAnalysisListener;
import com.rootcause.foshol.common.RejectionReason;
import com.rootcause.foshol.common.RemedyType;
import com.rootcause.foshol.common.Severity;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(classes = ReviewModuleTestApplication.class)
@Testcontainers(disabledWithoutDocker = true)
@Tag("integration")
@Import(ReviewTaskUniversalityIT.Stubs.class)
class ReviewTaskUniversalityIT {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final UUID CROP = UUID.fromString("01800000-0000-7000-8000-000000000001");
    private static final UUID DISEASE_D = UUID.fromString("01800000-0000-7000-8000-000000000101");
    private static final UUID DISEASE_HEALTHY = UUID.fromString("01800000-0000-7000-8000-000000000106");
    private static final UUID REMEDY_R1 = UUID.fromString("01800000-0000-7000-8000-00000000a001");
    private static final UUID REMEDY_R2 = UUID.fromString("01800000-0000-7000-8000-00000000a002");
    private static final UUID FARMER = UUID.fromString("01800000-0000-7000-8000-00000000b001");
    private static final UUID OFFICER = UUID.fromString("01800000-0000-7000-8000-00000000c001");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
                    DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("foshol")
            .withUsername("foshol")
            .withPassword("foshol");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("foshol.review.claim.ttl", () -> "PT15M");
        registry.add("foshol.review.sla", () -> "PT4H");
        registry.add("foshol.review.sweeper.interval", () -> "PT5S");
        registry.add("foshol.analysis.confidence.high", () -> "0.75");
        registry.add("foshol.analysis.confidence.low", () -> "0.45");
        registry.add("foshol.i18n.display-zone", () -> "Asia/Dhaka");
        registry.add("spring.task.scheduling.enabled", () -> "false");
    }

    @Autowired
    private CreateReviewTaskOnAnalysisListener listener;

    @Autowired
    private ReviewTaskRepository tasks;

    @Autowired
    private OfficerQueueQueryHandler queue;

    @Autowired
    private ClaimReviewTaskCommandHandler claim;

    @Autowired
    private ApproveCaseCommandHandler approve;

    @Autowired
    private ReviseAdvisoryCommandHandler revise;

    @Autowired
    private RejectCaseCommandHandler reject;

    @Autowired
    private SweepExpiredClaimsCommandHandler sweeper;

    @Autowired
    private AdminStatsQueryHandler stats;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    @Autowired
    private Stubs.Support support;

    @Test
    void everyAnalysedCaseGetsExactlyOneReviewTask() {
        seedIdentities();
        Instant tPrimary = T0;
        Instant tSecondary = T0.plusSeconds(10);
        Instant tUndetermined = T0.plusSeconds(20);
        Instant tUnmapped = T0.plusSeconds(30);
        Instant tFailed = T0.plusSeconds(40);
        UUID primary = insertCase(tPrimary);
        UUID secondary = insertCase(tSecondary);
        UUID undetermined = insertCase(tUndetermined);
        UUID unmapped = insertCase(tUnmapped);
        UUID failed = insertCase(tFailed);
        listener.onCompleted(completed(primary, DecisionPath.PRIMARY, "0.91"));
        listener.onCompleted(completed(secondary, DecisionPath.SECONDARY, "0.62"));
        listener.onCompleted(completed(undetermined, DecisionPath.UNDETERMINED, "0.30"));
        listener.onCompleted(completed(unmapped, DecisionPath.UNDETERMINED, "0.62"));
        listener.onFailed(new AnalysisFailed(failed, FARMER, "ERR_SIDECAR_UNAVAILABLE", "c", tFailed));
        listener.onCompleted(completed(primary, DecisionPath.PRIMARY, "0.91"));
        assertThat(tasks.count()).isEqualTo(5);
        assertThat(jdbc.queryForObject("select count(*) from geo_division", Long.class)).isEqualTo(8L);
        assertThat(jdbc.queryForObject("select count(*) from geo_district", Long.class)).isEqualTo(64L);
        assertThat(jdbc.queryForObject("select count(*) from review_task", Long.class)).isEqualTo(5L);
        var page = queue.handle(new OfficerQueueQuery("PENDING", false, OFFICER, "DHA", 0, 20, null, null));
        assertThat(page.content())
                .extracting(OfficerQueueRow::submittedAt)
                .containsExactly(tFailed, tUnmapped, tUndetermined, tSecondary, tPrimary);
        assertThat(page.content())
                .extracting(OfficerQueueRow::caseId)
                .containsExactly(failed, unmapped, undetermined, secondary, primary);

        UUID editTask = tasks.findByCaseId(primary).orElseThrow().id();
        claim.handle(new ClaimReviewTaskCommand(editTask, OFFICER));
        var edited = approve.handle(new ApproveCaseCommand(
                editTask, OFFICER, DISEASE_D, List.of(REMEDY_R1), null));
        assertThat(edited.advisory().action().name()).isEqualTo("EDITED");
        assertThat(tasks.findById(editTask).orElseThrow().state()).isEqualTo(ReviewState.DONE);

        var v2 = revise.handle(new ReviseAdvisoryCommand(
                edited.advisory().advisoryId(), OFFICER, DISEASE_HEALTHY, List.of(), null));
        assertThat(v2.advisory().version()).isEqualTo(2);
        assertThat(v2.advisory().supersedesId()).isEqualTo(edited.advisory().advisoryId());
        assertThat(jdbc.queryForObject(
                        "select version from advisory where id = ?", Short.class, edited.advisory().advisoryId()))
                .isEqualTo((short) 1);

        UUID rejectTask = tasks.findByCaseId(failed).orElseThrow().id();
        claim.handle(new ClaimReviewTaskCommand(rejectTask, OFFICER));
        reject.handle(new RejectCaseCommand(rejectTask, OFFICER, RejectionReason.BLURRY_IMAGE, "ছবি"));
        assertThat(tasks.findById(rejectTask).orElseThrow().state()).isEqualTo(ReviewState.REJECTED);
        org.junit.jupiter.api.Assertions.assertThrows(
                ReviewException.class,
                () -> approve.handle(new ApproveCaseCommand(
                        rejectTask, OFFICER, DISEASE_D, List.of(REMEDY_R1, REMEDY_R2), null)));

        UUID sweepCase = insertCase();
        listener.onCompleted(completed(sweepCase, DecisionPath.PRIMARY, "0.40"));
        UUID sweepTask = tasks.findByCaseId(sweepCase).orElseThrow().id();
        claim.handle(new ClaimReviewTaskCommand(sweepTask, OFFICER));
        clock.set(T0.plus(Duration.ofMinutes(16)));
        sweeper.handle();
        var swept = tasks.findById(sweepTask).orElseThrow();
        assertThat(swept.state()).isEqualTo(ReviewState.PENDING);
        assertThat(swept.requeueCount()).isEqualTo((short) 1);

        var admin = stats.handle(new AdminStatsQuery(OFFICER));
        assertThat(admin.confidenceHigh()).isEqualByComparingTo("0.75");
        assertThat(admin.agreementSampleSize()).isGreaterThanOrEqualTo(1);
        assertThat(admin.casesLifetime()).isGreaterThanOrEqualTo(1);
    }

    private void seedIdentities() {
        jdbc.update(
                "insert into farmer (id, name, phone_hash, phone_enc, district_code, division_code, preferred_language) values (?,?,?,?,?,?,?) on conflict do nothing",
                FARMER,
                "Farmer A",
                "a".repeat(64),
                new byte[] {1},
                "DHA",
                "DHK",
                "bn");
        jdbc.update(
                "insert into field_officer (id, name, username, password_hash, phone_hash, phone_enc, district_code, division_code, role, active) values (?,?,?,?,?,?,?,?,?,true) on conflict do nothing",
                OFFICER,
                "Officer A",
                "officer.a",
                "hash",
                "b".repeat(64),
                new byte[] {1},
                "DHA",
                "DHK",
                "OFFICER");
        jdbc.update(
                """
                insert into remedy (id, disease_id, type, title_bn, steps_bn, phi_days, cost_tier, efficacy, source_ref)
                values (?, ?, 'CULTURAL', '', '[]'::jsonb, null, 'LOW', 'LOW', '')
                on conflict do nothing
                """,
                REMEDY_R1,
                DISEASE_D);
        jdbc.update(
                """
                insert into remedy (id, disease_id, type, title_bn, steps_bn, phi_days, cost_tier, efficacy, source_ref)
                values (?, ?, 'CHEMICAL', '', '[]'::jsonb, 14, 'LOW', 'LOW', '')
                on conflict do nothing
                """,
                REMEDY_R2,
                DISEASE_D);
    }

    private UUID insertCase() {
        return insertCase(T0);
    }

    private UUID insertCase(Instant submittedAt) {
        UUID id = Uuid7.create();
        jdbc.update(
                "insert into diagnosis_case (id, farmer_id, crop_id, status, district_code, division_code, correlation_id) values (?,?,?,?,?,?,?)",
                id,
                FARMER,
                CROP,
                "ANALYSED",
                "DHA",
                "DHK",
                id.toString());
        jdbc.update(
                "insert into case_candidate (id, case_id, disease_id, confidence, rank, source) values (?,?,?,?,1,'MODEL')",
                Uuid7.create(),
                id,
                DISEASE_D,
                new BigDecimal("0.5000"));
        support.cases.put(
                id,
                new CaseSummary(
                        id,
                        FARMER,
                        CROP,
                        "rice",
                        "DHA",
                        "DHK",
                        com.rootcause.foshol.common.CaseStatus.ANALYSED,
                        DecisionPath.PRIMARY,
                        null,
                        null,
                        List.of(),
                        null,
                        "c",
                        submittedAt,
                        new BigDecimal("1"),
                        com.rootcause.foshol.common.FieldAreaUnit.DECIMAL,
                        null,
                        null,
                        com.rootcause.foshol.common.MetricsSource.FORM));
        return id;
    }

    private AnalysisCompleted completed(UUID caseId, DecisionPath path, String confidence) {
        BigDecimal top1 = confidence == null ? null : new BigDecimal(confidence);
        return new AnalysisCompleted(
                caseId,
                FARMER,
                CROP,
                path,
                AiMode.REPLAY,
                top1,
                new BigDecimal("0.10"),
                top1 == null
                        ? List.of()
                        : List.of(new CandidateView(DISEASE_D, "brown_spot", "d", top1, 1, CandidateSource.MODEL)),
                List.of(),
                false,
                1,
                "c",
                T0);
    }

    @TestConfiguration
    static class Stubs {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(T0);
        }

        @Bean
        Support support() {
            return new Support();
        }

        @Bean
        CaseIntakeApi caseIntakeApi(Support support) {
            return new CaseIntakeApi() {
                @Override
                public Optional<CaseSummary> findById(UUID caseId) {
                    return Optional.ofNullable(support.cases.get(caseId));
                }

                @Override
                public boolean isOwnedBy(UUID caseId, UUID farmerId) {
                    return findById(caseId).map(c -> c.farmerId().equals(farmerId)).orElse(false);
                }

                @Override
                public boolean officerSharesDistrict(UUID caseId, UUID officerId) {
                    return findById(caseId).map(c -> "DHA".equals(c.districtCode())).orElse(false);
                }

                @Override
                public void recordTranscript(UUID caseId, String transcriptBn, BigDecimal asrConfidence) {}

                @Override
                public void recordFieldMetrics(
                        UUID caseId,
                        BigDecimal fieldArea,
                        FieldAreaUnit fieldAreaUnit,
                        BigDecimal cropQuantity,
                        CropQuantityUnit cropQuantityUnit,
                        MetricsSource source) {}
            };
        }

        @Bean
        AnalysisApi analysisApi(Support support) {
            return new AnalysisApi() {
                @Override
                public Optional<AnalysisView> findByCaseId(UUID caseId) {
                    return Optional.of(new AnalysisView(
                            caseId,
                            DecisionPath.PRIMARY,
                            AiMode.REPLAY,
                            new BigDecimal("0.91"),
                            new BigDecimal("0.40"),
                            new BigDecimal("0.51"),
                            List.of(new CandidateView(
                                    DISEASE_D, "brown_spot", "d", new BigDecimal("0.91"), 1, CandidateSource.MODEL)),
                            List.of(),
                            null,
                            null,
                            null,
                            List.of(),
                            "m",
                            "v",
                            1,
                            null));
                }

                @Override
                public void recordOfficerSymptoms(UUID caseId, List<UUID> symptomIds) {}
            };
        }

        @Bean
        KnowledgeQueryApi knowledgeQueryApi() {
            return new KnowledgeQueryApi() {
                @Override
                public List<CropView> listCrops() {
                    return List.of();
                }

                @Override
                public Optional<CropView> findCropById(UUID cropId) {
                    return Optional.of(new CropView(CROP, "rice", "rice", "Rice", "crop-rice"));
                }

                @Override
                public Optional<CropView> findCropByCode(String code) {
                    return Optional.empty();
                }

                @Override
                public Optional<DiseaseView> findDiseaseById(UUID diseaseId) {
                    if (DISEASE_HEALTHY.equals(diseaseId)) {
                        return Optional.of(new DiseaseView(
                                DISEASE_HEALTHY, CROP, "healthy", "h", "h", null, Severity.NONE, true));
                    }
                    return Optional.of(new DiseaseView(DISEASE_D, CROP, "brown_spot", "d", "d", null, Severity.LOW, false));
                }

                @Override
                public List<DiseaseView> listDiseasesByCrop(UUID cropId) {
                    return List.of();
                }

                @Override
                public List<RemedyView> listActiveRemedies(UUID diseaseId) {
                    if (DISEASE_HEALTHY.equals(diseaseId)) {
                        return List.of();
                    }
                    return List.of(
                            new RemedyView(
                                    REMEDY_R1, DISEASE_D, RemedyType.CULTURAL, "", List.of(), null, null, "LOW", "LOW", "",
                                    null, null, null, null),
                            new RemedyView(
                                    REMEDY_R2, DISEASE_D, RemedyType.CHEMICAL, "", List.of(), null, 14, "LOW", "LOW", "",
                                    null, null, null, null));
                }

                @Override
                public List<SymptomRefView> listSymptoms() {
                    return List.of();
                }

                @Override
                public Optional<UUID> resolveModelLabel(String modelId, String modelVersion, String rawLabel) {
                    return Optional.empty();
                }
            };
        }

        @Bean
        FarmerLookupApi farmerLookupApi() {
            return new FarmerLookupApi() {
                @Override
                public Optional<FarmerView> findById(UUID farmerId) {
                    return Optional.of(new FarmerView(FARMER, "Farmer A", "DHA", "bn", "DHK"));
                }

                @Override
                public Optional<FarmerView> findByPhone(String e164Phone) {
                    return Optional.empty();
                }
            };
        }

        @Bean
        OfficerLookupApi officerLookupApi() {
            return new OfficerLookupApi() {
                @Override
                public Optional<OfficerView> findById(UUID officerId) {
                    return Optional.of(new OfficerView(OFFICER, "Officer A", "DHA", "OFFICER", true, "DHK"));
                }

                @Override
                public List<OfficerView> findActiveByDistrict(String districtCode) {
                    return List.of();
                }
            };
        }

        static final class Support {
            final Map<UUID, CaseSummary> cases = new ConcurrentHashMap<>();
        }
    }
}

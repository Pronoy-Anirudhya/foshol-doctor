package com.rootcause.foshol.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.rootcause.foshol.analysis.api.AnalysisApi;
import com.rootcause.foshol.analysis.api.AnalysisView;
import com.rootcause.foshol.analysis.application.command.RunAnalysisCommand;
import com.rootcause.foshol.analysis.application.command.handler.RunAnalysisCommandHandler;
import com.rootcause.foshol.analysis.application.port.ObjectStorePort;
import com.rootcause.foshol.common.AiMode;
import com.rootcause.foshol.common.CandidateSource;
import com.rootcause.foshol.common.CaseStatus;
import com.rootcause.foshol.common.ConfigKeys;
import com.rootcause.foshol.common.DecisionPath;
import com.rootcause.foshol.common.RemedyType;
import com.rootcause.foshol.common.Severity;
import com.rootcause.foshol.common.SymptomSource;
import com.rootcause.foshol.common.cqrs.CommandBus;
import com.rootcause.foshol.common.cqrs.CommandHandler;
import com.rootcause.foshol.common.cqrs.CqrsBuses;
import com.rootcause.foshol.common.cqrs.QueryBus;
import com.rootcause.foshol.common.cqrs.QueryHandler;
import com.rootcause.foshol.common.events.AnalysisCompleted;
import com.rootcause.foshol.common.events.CaseAudioRef;
import com.rootcause.foshol.common.events.CaseImageRef;
import com.rootcause.foshol.intake.api.CaseIntakeApi;
import com.rootcause.foshol.intake.api.CaseSummary;
import com.rootcause.foshol.knowledge.api.DiseaseView;
import com.rootcause.foshol.knowledge.api.KnowledgeQueryApi;
import com.rootcause.foshol.knowledge.api.MatchedSymptom;
import com.rootcause.foshol.knowledge.api.RemedyView;
import com.rootcause.foshol.knowledge.api.ScoredDisease;
import com.rootcause.foshol.knowledge.api.SymptomMatchApi;
import com.rootcause.foshol.knowledge.api.SymptomMatchResult;
import com.rootcause.foshol.knowledge.api.SymptomRefView;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = {
            AnalysisIntegrationTest.AnalysisTestApplication.class,
            AnalysisIntegrationTest.TestDoubles.class
        },
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AnalysisIntegrationTest {

    static final UUID CASE_ID = UUID.fromString("01800000-0000-7000-8000-00000000c001");
    static final UUID FARMER = UUID.fromString("01800000-0000-7000-8000-00000000f001");
    static final UUID CROP = UUID.fromString("01800000-0000-7000-8000-000000000001");
    static final UUID BROWN_SPOT = UUID.fromString("01800000-0000-7000-8000-000000000101");
    static final UUID BLAST = UUID.fromString("01800000-0000-7000-8000-000000000103");
    static final UUID HEALTHY = UUID.fromString("01800000-0000-7000-8000-000000000106");
    static final UUID IMAGE_A = UUID.fromString("01800000-0000-7000-8000-00000000aa01");
    static final UUID IMAGE_B = UUID.fromString("01800000-0000-7000-8000-00000000aa02");
    static final UUID AUDIO_ID = UUID.fromString("01800000-0000-7000-8000-00000000a001");
    static final UUID SYMPTOM = UUID.fromString("01800000-0000-7000-8000-000000005001");
    static final String HIGH_SHA = "8b3b8b23ce56af5cc3f864cf5fbca0f46b786901ec423237692c396cee9d1599";
    static final String HIGH_B_SHA = "9ee38e5617b0f15aed8951c05cc2de934295d5209a7bcfda86d0a589272f3dd9";
    static final String AUDIO_KEY = "cases/" + CASE_ID + "/audio.wav";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
                    DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("foshol")
            .withUsername("foshol")
            .withPassword("foshol");

    @Container
    static final GenericContainer<?> MINIO = new GenericContainer<>(
                    DockerImageName.parse("minio/minio:RELEASE.2025-04-22T22-12-26Z"))
            .withEnv("MINIO_ROOT_USER", "minioadmin")
            .withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
            .withCommand("server", "/data")
            .withExposedPorts(9000)
            .waitingFor(Wait.forListeningPort())
            .withStartupTimeout(Duration.ofMinutes(2));

    @BeforeAll
    static void requireDocker() {
        assumeThat(DockerClientFactory.instance().isDockerAvailable()).isTrue();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.open-in-view", () -> "false");
        registry.add("spring.flyway.locations", () -> "filesystem:" + flywayDir());
        registry.add("spring.main.web-application-type", () -> "none");
        registry.add(
                "spring.autoconfigure.exclude",
                () -> String.join(
                        ",",
                        "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration",
                        "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration",
                        "org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration",
                        "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration"));
        registry.add(ConfigKeys.AI_MODE, () -> "replay");
        registry.add(ConfigKeys.ANALYSIS_CONFIDENCE_HIGH, () -> "0.75");
        registry.add(ConfigKeys.ANALYSIS_CONFIDENCE_LOW, () -> "0.45");
        registry.add(ConfigKeys.ANALYSIS_CONFIDENCE_TEMPERATURE, () -> "1.0");
        registry.add(ConfigKeys.ANALYSIS_MULTI_IMAGE_AGGREGATION, () -> "MAX");
        registry.add(ConfigKeys.ANALYSIS_DEADLINE, () -> "PT3S");
        registry.add(ConfigKeys.ANALYSIS_CANDIDATE_LIMIT, () -> "5");
        registry.add(ConfigKeys.ANALYSIS_GRADCAM_ENABLED, () -> "true");
        registry.add(ConfigKeys.AI_BASE_URL, () -> "http://localhost:8000");
        registry.add(ConfigKeys.AI_TIMEOUT, () -> "PT8S");
        registry.add(ConfigKeys.STORAGE_PRESIGN_TTL, () -> "PT10M");
        registry.add(ConfigKeys.STORAGE_ENDPOINT, () -> "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
        registry.add(ConfigKeys.STORAGE_ACCESS_KEY, () -> "minioadmin");
        registry.add(ConfigKeys.STORAGE_SECRET_KEY, () -> "minioadmin");
        registry.add(ConfigKeys.STORAGE_BUCKET, () -> "foshol-cases");
        registry.add("resilience4j.circuitbreaker.instances.sidecar.sliding-window-size", () -> "10");
        registry.add("resilience4j.retry.instances.sidecar.max-attempts", () -> "2");
        registry.add("resilience4j.timelimiter.instances.sidecar.timeout-duration", () -> "8s");
    }

    private static String flywayDir() {
        String configured = System.getProperty("foshol.flyway.dir");
        if (configured != null && Files.isDirectory(Path.of(configured))) {
            return configured;
        }
        Path fallback = Path.of("app/src/main/resources/db/migration");
        if (Files.isDirectory(fallback)) {
            return fallback.toAbsolutePath().toString();
        }
        return Path.of("../..", "app/src/main/resources/db/migration").toAbsolutePath().normalize().toString();
    }

    @Autowired
    RunAnalysisCommandHandler handler;

    @Autowired
    AnalysisApi analysisApi;

    @Autowired
    ObjectStorePort objectStore;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    CaseIntakeApi intake;

    @Autowired
    KnowledgeQueryApi knowledge;

    @Autowired
    SymptomMatchApi symptomMatch;

    @Autowired
    CompletedSink completedSink;

    @BeforeEach
    void seed() {
        completedSink.events.clear();
        Mockito.reset(intake, knowledge, symptomMatch);
        jdbc.update("delete from case_symptom");
        jdbc.update("delete from case_candidate");
        jdbc.update("delete from analysis_run");
        jdbc.update("delete from case_audio");
        jdbc.update("delete from case_image");
        jdbc.update("delete from diagnosis_case");
        jdbc.update("delete from farmer");
        jdbc.update("delete from symptom where id = ?", SYMPTOM);
        jdbc.update(
                """
                insert into farmer (id, name, phone_hash, phone_enc, district_code, preferred_language)
                values (?, 'IT Farmer', 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', ?, 'BD-13', 'bn')
                """,
                FARMER,
                new byte[] {0});
        jdbc.update(
                """
                insert into diagnosis_case (id, farmer_id, crop_id, status, district_code, correlation_id)
                values (?, ?, ?, 'SUBMITTED', 'BD-13', 'corr-it')
                """,
                CASE_ID,
                FARMER,
                CROP);
        jdbc.update(
                """
                insert into symptom (id, code, name_bn, organ)
                values (?, 'it_placeholder', 'TODO(content-owner)', 'LEAF')
                """,
                SYMPTOM);
        objectStore.write(AUDIO_KEY, "fixture:asr:demo".getBytes(StandardCharsets.UTF_8), "audio/wav");
        when(intake.findById(CASE_ID)).thenReturn(Optional.of(summary()));
        when(knowledge.resolveModelLabel(anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            String label = invocation.getArgument(2);
            if ("Brown Spot".equals(label)) {
                return Optional.of(BROWN_SPOT);
            }
            if ("Leaf Blast".equals(label)) {
                return Optional.of(BLAST);
            }
            if ("Healthy".equals(label)) {
                return Optional.of(HEALTHY);
            }
            return Optional.empty();
        });
        when(knowledge.findDiseaseById(BROWN_SPOT))
                .thenReturn(Optional.of(disease(BROWN_SPOT, "brown_spot", false)));
        when(knowledge.findDiseaseById(BLAST)).thenReturn(Optional.of(disease(BLAST, "blast", false)));
        when(knowledge.findDiseaseById(HEALTHY)).thenReturn(Optional.of(disease(HEALTHY, "healthy", true)));
        when(knowledge.listActiveRemedies(BROWN_SPOT)).thenReturn(List.of(new RemedyView(
                UUID.randomUUID(),
                BROWN_SPOT,
                RemedyType.CULTURAL,
                "TODO(content-owner)",
                List.of("TODO(content-owner)"),
                null,
                null,
                "LOW",
                "HIGH",
                "TODO(content-owner)")));
        when(knowledge.listActiveRemedies(BLAST)).thenReturn(List.of());
        when(knowledge.listActiveRemedies(HEALTHY)).thenReturn(List.of());
        when(knowledge.listSymptoms())
                .thenReturn(List.of(new SymptomRefView(SYMPTOM, "it_placeholder", "TODO(content-owner)", null, "LEAF")));
        when(symptomMatch.match(any())).thenReturn(new SymptomMatchResult(
                List.of(new MatchedSymptom(
                        SYMPTOM, "it_placeholder", "TODO(content-owner)", new BigDecimal("0.812"), "VECTOR")),
                List.of(new ScoredDisease(BROWN_SPOT, "brown_spot", "Brown spot", new BigDecimal("0.70"), 1)),
                false));
    }

    @Test
    void replayPrimaryPathPersistsRunCandidatesSymptomsAndGradcam() {
        handler.handle(new RunAnalysisCommand(
                CASE_ID, FARMER, CROP, "rice", images(), audio(), "corr-it"));
        String path = jdbc.queryForObject(
                "select decision_path from analysis_run where case_id = ?", String.class, CASE_ID);
        String mode = jdbc.queryForObject("select mode from analysis_run where case_id = ?", String.class, CASE_ID);
        String gradcamKey = jdbc.queryForObject(
                "select gradcam_object_key from analysis_run where case_id = ?", String.class, CASE_ID);
        Integer candidates = jdbc.queryForObject(
                "select count(*) from case_candidate where case_id = ? and source = 'MODEL'", Integer.class, CASE_ID);
        Integer visionSymptoms = jdbc.queryForObject(
                "select count(*) from case_symptom where case_id = ? and source = 'VISION'", Integer.class, CASE_ID);
        Integer speechSymptoms = jdbc.queryForObject(
                "select count(*) from case_symptom where case_id = ? and source = 'SPEECH'", Integer.class, CASE_ID);
        assertThat(path).isEqualTo(DecisionPath.PRIMARY.name());
        assertThat(mode).isEqualTo(AiMode.REPLAY.name());
        assertThat(candidates).isGreaterThanOrEqualTo(1);
        assertThat(visionSymptoms).isZero();
        assertThat(speechSymptoms).isEqualTo(1);
        assertThat(gradcamKey).isEqualTo("cases/" + CASE_ID + "/gradcam/" + IMAGE_A + ".png");
        assertThat(objectStore.read(gradcamKey)).isNotEmpty();
        assertThat(completedSink.events).hasSize(1);
        AnalysisCompleted event = completedSink.events.get(0);
        assertThat(event.decisionPath()).isEqualTo(DecisionPath.PRIMARY);
        assertThat(event.hasAudio()).isTrue();
        assertThat(event.imageCount()).isEqualTo(2);
        assertThat(event.candidates()).isNotEmpty();
        assertThat(event.candidates().get(0).source()).isEqualTo(CandidateSource.MODEL);
        Optional<AnalysisView> view = analysisApi.findByCaseId(CASE_ID);
        assertThat(view).isPresent();
        assertThat(view.get().decisionPath()).isEqualTo(DecisionPath.PRIMARY);
        assertThat(view.get().symptoms()).extracting(s -> s.source()).contains(SymptomSource.SPEECH);
    }

    private static CaseSummary summary() {
        return new CaseSummary(
                CASE_ID,
                FARMER,
                CROP,
                "rice",
                "BD-13",
                CaseStatus.SUBMITTED,
                null,
                null,
                null,
                images(),
                audio(),
                "corr-it",
                Instant.parse("2026-09-07T00:00:00Z"));
    }

    private static List<CaseImageRef> images() {
        return List.of(
                new CaseImageRef(IMAGE_A, "img-a", null, HIGH_SHA, new BigDecimal("0.900"), true, 1),
                new CaseImageRef(IMAGE_B, "img-b", null, HIGH_B_SHA, new BigDecimal("0.600"), false, 2));
    }

    private static CaseAudioRef audio() {
        return new CaseAudioRef(AUDIO_ID, AUDIO_KEY, 1000, null);
    }

    private static DiseaseView disease(UUID id, String code, boolean healthy) {
        return new DiseaseView(id, CROP, code, code, code, null, healthy ? Severity.NONE : Severity.LOW, healthy);
    }

    static class CompletedSink {
        final List<AnalysisCompleted> events = new CopyOnWriteArrayList<>();

        @EventListener
        void on(AnalysisCompleted event) {
            events.add(event);
        }
    }

    @Configuration
    static class TestDoubles {
        @Bean
        CaseIntakeApi caseIntakeApi() {
            return Mockito.mock(CaseIntakeApi.class);
        }

        @Bean
        KnowledgeQueryApi knowledgeQueryApi() {
            return Mockito.mock(KnowledgeQueryApi.class);
        }

        @Bean
        SymptomMatchApi symptomMatchApi() {
            return Mockito.mock(SymptomMatchApi.class);
        }

        @Bean
        CompletedSink completedSink() {
            return new CompletedSink();
        }
    }

    @SpringBootApplication(scanBasePackages = "com.rootcause.foshol.analysis")
    static class AnalysisTestApplication {

        @Bean
        CommandBus commandBus(ObjectProvider<CommandHandler<?, ?>> handlers) {
            return CqrsBuses.commandBus(handlers.orderedStream().toList());
        }

        @Bean
        QueryBus queryBus(ObjectProvider<QueryHandler<?, ?>> handlers) {
            return CqrsBuses.queryBus(handlers.orderedStream().toList());
        }
    }
}

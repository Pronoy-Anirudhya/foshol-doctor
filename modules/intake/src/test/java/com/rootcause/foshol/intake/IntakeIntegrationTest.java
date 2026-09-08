package com.rootcause.foshol.intake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.rootcause.foshol.common.ConfigKeys;
import com.rootcause.foshol.common.ErrorCodes;
import com.rootcause.foshol.common.FieldAreaUnit;
import com.rootcause.foshol.common.cqrs.CommandBus;
import com.rootcause.foshol.common.cqrs.CommandHandler;
import com.rootcause.foshol.common.cqrs.CqrsBuses;
import com.rootcause.foshol.common.cqrs.QueryBus;
import com.rootcause.foshol.common.cqrs.QueryHandler;
import com.rootcause.foshol.identity.api.FarmerLookupApi;
import com.rootcause.foshol.identity.api.FarmerView;
import com.rootcause.foshol.intake.api.IntakeImage;
import com.rootcause.foshol.intake.api.IntakeRequest;
import com.rootcause.foshol.intake.application.IntakeException;
import com.rootcause.foshol.intake.application.command.handler.SubmitCaseCommandHandler;
import com.rootcause.foshol.intake.application.command.SubmitCaseResult;
import com.rootcause.foshol.intake.infrastructure.InMemoryObjectStore;
import com.rootcause.foshol.knowledge.api.CropView;
import com.rootcause.foshol.knowledge.api.KnowledgeQueryApi;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = IntakeIntegrationTest.IntakeTestApplication.class)
class IntakeIntegrationTest {

    private static final UUID FARMER = UUID.fromString("01800000-0000-7000-8000-000000000201");
    private static final UUID CROP = UUID.fromString("01800000-0000-7000-8000-000000000001");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
                    DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("foshol")
            .withUsername("foshol")
            .withPassword("foshol");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        assumeThat(DockerClientFactory.instance().isDockerAvailable()).isTrue();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.open-in-view", () -> "false");
        registry.add("spring.flyway.locations", () -> "filesystem:" + flywayDir());
        registry.add(ConfigKeys.STORAGE_ENDPOINT, () -> "memory");
        registry.add(ConfigKeys.STORAGE_ACCESS_KEY, () -> "test");
        registry.add(ConfigKeys.STORAGE_SECRET_KEY, () -> "test");
        registry.add(ConfigKeys.STORAGE_BUCKET, () -> "foshol-cases");
        registry.add(ConfigKeys.STORAGE_DERIVATIVE_MAX_EDGE_PX, () -> "1024");
        registry.add(ConfigKeys.STORAGE_PRESIGN_TTL, () -> "PT10M");
        registry.add(ConfigKeys.INTAKE_MIN_IMAGES, () -> "1");
        registry.add(ConfigKeys.INTAKE_MAX_IMAGES, () -> "3");
        registry.add(ConfigKeys.INTAKE_MAX_IMAGE_BYTES, () -> "8388608");
        registry.add(ConfigKeys.INTAKE_ALLOWED_IMAGE_TYPES, () -> "image/jpeg,image/png,image/webp");
        registry.add(ConfigKeys.INTAKE_MAX_AUDIO_SECONDS, () -> "30");
        registry.add(ConfigKeys.INTAKE_MAX_AUDIO_BYTES, () -> "4194304");
        registry.add(ConfigKeys.INTAKE_ALLOWED_AUDIO_TYPES, () -> "audio/wav,audio/webm,audio/ogg,audio/mp4");
        registry.add(ConfigKeys.INTAKE_IDEMPOTENCY_TTL, () -> "PT24H");
        registry.add(ConfigKeys.INTAKE_RATE_LIMIT_MAX_CASES, () -> "20");
        registry.add(ConfigKeys.INTAKE_RATE_LIMIT_WINDOW, () -> "PT1H");
        registry.add(ConfigKeys.INTAKE_QUALITY_BLUR_VARIANCE_MIN, () -> "60.0");
        registry.add(ConfigKeys.INTAKE_QUALITY_EXPOSURE_MIN, () -> "0.15");
        registry.add(ConfigKeys.INTAKE_QUALITY_EXPOSURE_MAX, () -> "0.90");
        registry.add(ConfigKeys.INTAKE_QUALITY_MIN_EDGE_PX, () -> "224");
        registry.add(ConfigKeys.INTAKE_QUALITY_VEGETATION_COVERAGE_MIN, () -> "0.12");
    }

    private static String flywayDir() {
        String configured = System.getProperty("foshol.flyway.dir");
        if (configured != null && Files.isDirectory(Path.of(configured))) {
            return configured;
        }
        return Path.of("app/src/main/resources/db/migration").toAbsolutePath().toString();
    }

    @Autowired
    SubmitCaseCommandHandler submit;

    @Autowired
    DataSource dataSource;

    @Autowired
    InMemoryObjectStore store;

    @BeforeEach
    void seedFarmer() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("delete from p_farmer_case_history");
        jdbc.update("delete from idempotency_key");
        jdbc.update("delete from case_audio");
        jdbc.update("delete from case_image");
        jdbc.update("delete from diagnosis_case");
        jdbc.update("delete from farmer where id = ?", FARMER);
        jdbc.update(
                """
                insert into farmer (id, name, phone_hash, phone_enc, district_code, division_code, preferred_language)
                values (?, 'IT Farmer', ?, ?, 'DHA', 'DHK', 'bn')
                """,
                FARMER,
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                new byte[] {1, 2, 3, 4});
    }

    @Test
    void sharpSubmissionPersistsAndQualityFailureStoresNothing() {
        SubmitCaseResult accepted = submit.handle(new IntakeRequest(
                FARMER,
                CROP,
                null,
                null,
                BigDecimal.ONE,
                FieldAreaUnit.DECIMAL,
                null,
                null,
                List.of(new IntakeImage("", "image/jpeg", IntakeFixtures.sharpJpeg())),
                null,
                UUID.randomUUID()));
        assertThat(accepted.replayed()).isFalse();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Integer stored = jdbc.queryForObject("select count(*) from diagnosis_case", Integer.class);
        assertThat(stored).isEqualTo(1);
        assertThat(store.get(jdbc.queryForObject("select object_key from case_image", String.class))).isNotEmpty();

        jdbc.update("delete from p_farmer_case_history");
        jdbc.update("delete from idempotency_key");
        jdbc.update("delete from case_audio");
        jdbc.update("delete from case_image");
        jdbc.update("delete from diagnosis_case");

        assertThatThrownBy(() -> submit.handle(new IntakeRequest(
                        FARMER,
                        CROP,
                        null,
                        null,
                        BigDecimal.ONE,
                        FieldAreaUnit.DECIMAL,
                        null,
                        null,
                        List.of(new IntakeImage("", "image/png", IntakeFixtures.blurredJpeg())),
                        null,
                        UUID.randomUUID())))
                .isInstanceOf(IntakeException.class)
                .satisfies(ex -> {
                    IntakeException intake = (IntakeException) ex;
                    assertThat(intake.errorCode()).isEqualTo(ErrorCodes.ERR_IMAGE_QUALITY_REJECTED);
                    assertThat(intake.status()).isEqualTo(422);
                });

        assertThat(jdbc.queryForObject("select count(*) from diagnosis_case", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from idempotency_key", Integer.class)).isZero();
    }

    @SpringBootApplication(scanBasePackages = "com.rootcause.foshol.intake")
    @EnableMethodSecurity
    static class IntakeTestApplication {

        @Bean
        FarmerLookupApi farmerLookupApi() {
            FarmerLookupApi api = mock(FarmerLookupApi.class);
            when(api.findById(FARMER)).thenReturn(Optional.of(new FarmerView(FARMER, "IT Farmer", "DHA", "bn", "DHK")));
            return api;
        }

        @Bean
        KnowledgeQueryApi knowledgeQueryApi() {
            KnowledgeQueryApi api = mock(KnowledgeQueryApi.class);
            when(api.findCropById(any())).thenAnswer(invocation -> {
                UUID id = invocation.getArgument(0);
                if (CROP.equals(id)) {
                    return Optional.of(new CropView(CROP, "rice", "rice", "Rice", "crop-rice"));
                }
                return Optional.empty();
            });
            return api;
        }

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            return http.csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .build();
        }

        @Bean
        CommandBus commandBus() {
            return new CommandBus();
        }

        @Bean
        QueryBus queryBus() {
            return new QueryBus();
        }

        @Bean
        ApplicationRunner registerCqrsHandlers(
                CommandBus commands,
                QueryBus queries,
                ObjectProvider<CommandHandler<?, ?>> commandHandlers,
                ObjectProvider<QueryHandler<?, ?>> queryHandlers) {
            return args -> {
                CqrsBuses.registerCommands(commands, commandHandlers.orderedStream().toList());
                CqrsBuses.registerQueries(queries, queryHandlers.orderedStream().toList());
            };
        }
    }
}

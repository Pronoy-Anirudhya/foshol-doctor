package com.rootcause.foshol.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;

import com.rootcause.foshol.common.ConfigKeys;
import com.rootcause.foshol.common.ErrorCodes;
import com.rootcause.foshol.knowledge.api.KnowledgeQueryApi;
import com.rootcause.foshol.knowledge.api.SymptomMatchApi;
import com.rootcause.foshol.knowledge.api.SymptomMatchRequest;
import com.rootcause.foshol.knowledge.api.SymptomMatchResult;
import com.rootcause.foshol.knowledge.domain.BanglaTextNormaliser;
import com.rootcause.foshol.knowledge.domain.KnowledgeException;
import com.rootcause.foshol.knowledge.domain.KnowledgeTaxonomy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = KnowledgeIntegrationTest.KnowledgeTestApplication.class)
class KnowledgeIntegrationTest {

    private static final UUID RICE = UUID.fromString("01800000-0000-7000-8000-000000000001");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
                    DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("foshol")
            .withUsername("foshol")
            .withPassword("foshol");

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
        registry.add(ConfigKeys.KNOWLEDGE_MATCH_VECTOR_THRESHOLD, () -> "0.72");
        registry.add(ConfigKeys.KNOWLEDGE_MATCH_FUZZY_THRESHOLD, () -> "0.60");
        registry.add(ConfigKeys.KNOWLEDGE_MATCH_MAX_SYMPTOMS, () -> "8");
        registry.add(ConfigKeys.KNOWLEDGE_MATCH_KNN_LIMIT, () -> "25");
        registry.add(ConfigKeys.KNOWLEDGE_MATCH_INCONCLUSIVE_SCORE_MIN, () -> "0.30");
        registry.add(ConfigKeys.AI_VISION_RICE_MODEL_ID, () -> "kssrikar4/Rice-Leaf-Disease-Classification");
        registry.add(ConfigKeys.AI_VISION_RICE_FALLBACK_MODEL_ID, () -> "prithivMLmods/Rice-Leaf-Disease");
        registry.add(ConfigKeys.AI_VISION_SOLANACEAE_MODEL_ID, () -> "Daksh159/plant-disease-mobilenetv2");
    }

    private static String flywayDir() {
        String configured = System.getProperty("foshol.flyway.dir");
        if (configured != null && Files.isDirectory(Path.of(configured))) {
            return configured;
        }
        Path fallback = Path.of("app/src/main/resources/db/migration");
        return fallback.toAbsolutePath().toString();
    }

    @Autowired
    KnowledgeQueryApi knowledgeQueryApi;

    @Autowired
    SymptomMatchApi symptomMatchApi;

    @Autowired
    DataSource dataSource;

    @Test
    void seededTaxonomyAndEmptyMatcher() {
        assertThat(knowledgeQueryApi.listCrops()).hasSize(3);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Integer diseases = jdbc.queryForObject("select count(*) from disease where deleted_at is null", Integer.class);
        assertThat(diseases).isEqualTo(KnowledgeTaxonomy.DISEASE_CLASS_COUNT);

        List<NormalisedPhrase> phrases = jdbc.query(
                "select phrase_bn, normalised_bn from symptom_phrase where deleted_at is null",
                (rs, rowNum) -> new NormalisedPhrase(rs.getString("phrase_bn"), rs.getString("normalised_bn")));
        for (NormalisedPhrase phrase : phrases) {
            assertThat(phrase.normalisedBn()).isEqualTo(BanglaTextNormaliser.normalise(phrase.phraseBn()));
        }

        SymptomMatchResult empty = symptomMatchApi.match(new SymptomMatchRequest(RICE, "পাতা", null, List.of()));
        assertThat(empty.inconclusive()).isTrue();
        assertThat(empty.symptoms()).isEmpty();
        assertThat(empty.diseases()).isEmpty();

        SymptomMatchResult again = symptomMatchApi.match(new SymptomMatchRequest(RICE, "পাতা", null, List.of()));
        assertThat(again).isEqualTo(empty);

        assertThat(knowledgeQueryApi.resolveModelLabel("any", "1", "Leaf_Blast")).isEmpty();
        assertThatThrownBy(() -> symptomMatchApi.match(
                        new SymptomMatchRequest(UUID.fromString("01800000-0000-7000-8000-000000000099"), null, null, List.of())))
                .isInstanceOf(KnowledgeException.class)
                .extracting(ex -> ((KnowledgeException) ex).errorCode())
                .isEqualTo(ErrorCodes.ERR_CROP_NOT_FOUND);
    }

    private record NormalisedPhrase(String phraseBn, String normalisedBn) {}

    @SpringBootApplication(scanBasePackages = "com.rootcause.foshol.knowledge")
    static class KnowledgeTestApplication {}
}

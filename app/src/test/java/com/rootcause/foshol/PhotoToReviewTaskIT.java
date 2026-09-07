package com.rootcause.foshol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.rootcause.foshol.common.Uuid7;
import com.rootcause.foshol.identity.domain.PhoneHash;
import com.rootcause.foshol.identity.domain.PhoneNumber;
import com.rootcause.foshol.identity.infrastructure.FarmerEntity;
import com.rootcause.foshol.identity.infrastructure.FarmerJpaRepository;
import com.rootcause.foshol.identity.infrastructure.FieldOfficerEntity;
import com.rootcause.foshol.identity.infrastructure.FieldOfficerJpaRepository;
import com.rootcause.foshol.identity.infrastructure.OtpChallengeJpaRepository;
import com.rootcause.foshol.identity.infrastructure.PhoneCipher;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@Tag("integration")
class PhotoToReviewTaskIT {

    private static final UUID RICE = UUID.fromString("01800000-0000-7000-8000-000000000001");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg17")
            .withDatabaseName("foshol")
            .withUsername("foshol")
            .withPassword("foshol");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("foshol.datasource.read-only.url", POSTGRES::getJdbcUrl);
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    FarmerJpaRepository farmers;

    @Autowired
    FieldOfficerJpaRepository officers;

    @Autowired
    OtpChallengeJpaRepository challenges;

    @Autowired
    PhoneCipher phoneCipher;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        OperationalTableWipe.wipeOperationalTables(jdbc);
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        farmers.save(new FarmerEntity(
                Uuid7.create(),
                "IT Farmer",
                PhoneHash.of(PhoneNumber.parse("+8801700000001")).hex(),
                phoneCipher.encrypt("+8801700000001"),
                "DHA",
                "bn",
                now,
                now));
        officers.save(new FieldOfficerEntity(
                Uuid7.create(),
                "IT Officer",
                "it_officer",
                passwordEncoder.encode("password"),
                PhoneHash.of(PhoneNumber.parse("+8801700000002")).hex(),
                phoneCipher.encrypt("+8801700000002"),
                "DHA",
                "OFFICER",
                true,
                now,
                now));
    }

    @Test
    void photoSubmissionCreatesOfficerReviewTaskInReplay() throws Exception {
        String farmerToken = farmerToken();
        String officerToken = officerToken();
        UUID idempotencyKey = Uuid7.create();
        MockMultipartFile image = new MockMultipartFile("images", "leaf.jpg", "image/jpeg", sharpJpeg());

        MvcResult created = mockMvc.perform(multipart("/api/v1/cases")
                        .file(image)
                        .param("cropId", RICE.toString())
                        .header("Authorization", "Bearer " + farmerToken)
                        .header("Idempotency-Key", idempotencyKey.toString()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andReturn();
        String caseId = JsonPath.read(created.getResponse().getContentAsString(), "$.caseId");

        mockMvc.perform(multipart("/api/v1/cases")
                        .file(new MockMultipartFile("images", "leaf.jpg", "image/jpeg", sharpJpeg()))
                        .param("cropId", RICE.toString())
                        .header("Authorization", "Bearer " + farmerToken)
                        .header("Idempotency-Key", idempotencyKey.toString()))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.caseId").value(caseId));

        Instant deadline = Instant.now().plus(Duration.ofSeconds(25));
        int count = 0;
        String body = "";
        while (Instant.now().isBefore(deadline)) {
            MvcResult queue = mockMvc.perform(get("/api/v1/review/queue")
                            .header("Authorization", "Bearer " + officerToken))
                    .andExpect(status().isOk())
                    .andReturn();
            body = queue.getResponse().getContentAsString();
            count = JsonPath.read(body, "$.content.length()");
            if (count >= 1) {
                break;
            }
            Thread.sleep(250);
        }
        assertThat(count).isGreaterThanOrEqualTo(1);
        assertThat((String) JsonPath.read(body, "$.content[0].caseId")).isEqualTo(caseId);
        assertThat((String) JsonPath.read(body, "$.content[0].reviewTaskId")).isNotBlank();
        assertThat((String) JsonPath.read(body, "$.content[0].state")).isEqualTo("PENDING");
    }

    private String farmerToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"+8801700000001\"}"))
                .andExpect(status().isAccepted());
        assertThat(challenges.count()).isEqualTo(1);
        MvcResult login = mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"+8801700000001\",\"code\":\"000000\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(login.getResponse().getContentAsString(), "$.token");
    }

    private String officerToken() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/officer/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"it_officer\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(login.getResponse().getContentAsString(), "$.token");
    }

    private static byte[] sharpJpeg() {
        BufferedImage image = new BufferedImage(320, 320, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        for (int y = 0; y < 320; y++) {
            for (int x = 0; x < 320; x++) {
                boolean on = ((x / 8) + (y / 8)) % 2 == 0;
                g.setColor(on ? Color.BLACK : Color.WHITE);
                g.fillRect(x, y, 1, 1);
            }
        }
        g.dispose();
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "jpeg", out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}

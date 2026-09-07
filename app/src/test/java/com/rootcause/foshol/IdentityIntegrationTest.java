package com.rootcause.foshol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rootcause.foshol.common.Uuid7;
import com.rootcause.foshol.identity.domain.PhoneHash;
import com.rootcause.foshol.identity.domain.PhoneNumber;
import com.rootcause.foshol.identity.infrastructure.FarmerEntity;
import com.rootcause.foshol.identity.infrastructure.FarmerJpaRepository;
import com.rootcause.foshol.identity.infrastructure.FieldOfficerEntity;
import com.rootcause.foshol.identity.infrastructure.FieldOfficerJpaRepository;
import com.rootcause.foshol.identity.infrastructure.OtpChallengeJpaRepository;
import com.rootcause.foshol.identity.infrastructure.PhoneCipher;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.jayway.jsonpath.JsonPath;
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
class IdentityIntegrationTest {

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
        jdbc.update("delete from otp_challenge");
        jdbc.update("delete from field_officer");
        jdbc.update("delete from farmer");
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        String farmerPhone = "+8801700000001";
        farmers.save(new FarmerEntity(
                Uuid7.create(),
                "IT Farmer",
                PhoneHash.of(PhoneNumber.parse(farmerPhone)).hex(),
                phoneCipher.encrypt(farmerPhone),
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
    void farmerOtpAndOfficerLoginIssueTokensForMe() throws Exception {
        mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"+8801700000001\"}"))
                .andExpect(status().isAccepted());
        assertThat(challenges.count()).isEqualTo(1);

        MvcResult farmerLogin = mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"+8801700000001\",\"code\":\"000000\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.principal.role").value("FARMER"))
                .andReturn();
        String farmerToken = token(farmerLogin);
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + farmerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("IT Farmer"));

        MvcResult officerLogin = mockMvc.perform(post("/api/v1/auth/officer/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"it_officer\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.principal.role").value("OFFICER"))
                .andReturn();
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token(officerLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("it_officer"));
    }

    private static String token(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }
}

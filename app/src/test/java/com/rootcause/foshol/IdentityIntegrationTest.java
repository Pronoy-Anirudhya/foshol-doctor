package com.rootcause.foshol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
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

    @Autowired
    com.rootcause.foshol.identity.api.OfficerLookupApi officerLookupApi;

    @BeforeEach
    void seed() {
        jdbc.update("delete from otp_challenge");
        jdbc.update("delete from farmer_provision_idempotency");
        jdbc.update("delete from farmer");
        jdbc.update("delete from field_officer");
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        String farmerPhone = "+8801700000001";
        farmers.save(new FarmerEntity(
                Uuid7.create(),
                "IT Farmer",
                PhoneHash.of(PhoneNumber.parse(farmerPhone)).hex(),
                phoneCipher.encrypt(farmerPhone),
                "DHA",
                "DHK",
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
                "DHK",
                "OFFICER",
                true,
                now,
                now));
        officers.save(new FieldOfficerEntity(
                Uuid7.create(),
                "IT Admin",
                "it_admin",
                passwordEncoder.encode("password"),
                PhoneHash.of(PhoneNumber.parse("+8801700000003")).hex(),
                phoneCipher.encrypt("+8801700000003"),
                "DHA",
                "DHK",
                "ADMIN",
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
                .andExpect(jsonPath("$.name").value("IT Farmer"))
                .andExpect(jsonPath("$.districtCode").value("DHA"))
                .andExpect(jsonPath("$.divisionCode").value("DHK"))
                .andExpect(jsonPath("$.districtNameBn").value("ঢাকা"))
                .andExpect(jsonPath("$.divisionNameBn").exists());

        mockMvc.perform(get("/api/v1/geo/divisions").header("Authorization", "Bearer " + farmerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(8));
        mockMvc.perform(get("/api/v1/geo/divisions/DHK/districts").header("Authorization", "Bearer " + farmerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code=='DHA')].nameEn", org.hamcrest.Matchers.hasItem("Dhaka")));
        mockMvc.perform(get("/api/v1/geo/divisions/NOPE/districts").header("Authorization", "Bearer " + farmerToken))
                .andExpect(status().isNotFound());

        MvcResult officerLogin = mockMvc.perform(post("/api/v1/auth/officer/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"it_officer\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.principal.role").value("OFFICER"))
                .andReturn();
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token(officerLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("it_officer"));
        assertThat(officerLookupApi.findActiveByDistrict("DHA"))
                .extracting(com.rootcause.foshol.identity.api.OfficerView::divisionCode)
                .contains("DHK");
        assertThat(officerLookupApi.findById(officers.findAll().getFirst().getId()))
                .get()
                .extracting(com.rootcause.foshol.identity.api.OfficerView::divisionCode)
                .isEqualTo("DHK");
    }

    @Test
    void officerAndAdminCanProvisionFarmersInTheirDistrict() throws Exception {
        String officerToken = officerToken("it_officer");
        String adminToken = officerToken("it_admin");
        mockMvc.perform(get("/api/v1/farmers/import/template").header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("farmer-import-template.csv")));
        String key = Uuid7.create().toString();
        MvcResult created = mockMvc.perform(post("/api/v1/farmers")
                        .header("Authorization", "Bearer " + officerToken)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"name\":\"Rahim Uddin\",\"phone\":\"+8801700000099\",\"divisionCode\":\"DHK\",\"districtCode\":\"DHA\",\"preferredLanguage\":\"bn\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Rahim Uddin"))
                .andExpect(jsonPath("$.districtCode").value("DHA"))
                .andExpect(jsonPath("$.source").value("MANUAL"))
                .andExpect(jsonPath("$.phone").doesNotExist())
                .andReturn();
        String farmerId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        mockMvc.perform(get("/api/v1/farmers/" + farmerId).header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Rahim Uddin"))
                .andExpect(jsonPath("$.phone").doesNotExist());
        mockMvc.perform(get("/api/v1/farmers/" + Uuid7.create()).header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ERR_FARMER_NOT_FOUND"));

        mockMvc.perform(post("/api/v1/farmers")
                        .header("Authorization", "Bearer " + officerToken)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"name\":\"Rahim Uddin\",\"phone\":\"+8801700000099\",\"divisionCode\":\"DHK\",\"districtCode\":\"DHA\",\"preferredLanguage\":\"bn\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed", "true"));

        mockMvc.perform(post("/api/v1/farmers")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("Idempotency-Key", Uuid7.create().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"name\":\"Karim\",\"phone\":\"+8801700000099\",\"divisionCode\":\"DHK\",\"districtCode\":\"DHA\",\"preferredLanguage\":\"bn\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ERR_FARMER_PHONE_EXISTS"));

        mockMvc.perform(post("/api/v1/farmers")
                        .header("Authorization", "Bearer " + officerToken)
                        .header("Idempotency-Key", Uuid7.create().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"name\":\"Out\",\"phone\":\"+8801700000088\",\"divisionCode\":\"CTG\",\"districtCode\":\"CTG\",\"preferredLanguage\":\"bn\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR_DISTRICT_SCOPE"));

        String farmerToken = farmerToken();
        mockMvc.perform(post("/api/v1/farmers")
                        .header("Authorization", "Bearer " + farmerToken)
                        .header("Idempotency-Key", Uuid7.create().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"name\":\"Nope\",\"phone\":\"+8801700000077\",\"divisionCode\":\"DHK\",\"districtCode\":\"DHA\",\"preferredLanguage\":\"bn\"}"))
                .andExpect(status().isForbidden());

        String csv = "name,phone,divisionCode,districtCode,preferredLanguage\n"
                + "CSV Farmer,+8801700000066,DHK,DHA,bn\n"
                + "Bad,,DHK,DHA,bn\n";
        MockMultipartFile file = new MockMultipartFile("file", "farmers.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v1/farmers/import")
                        .file(file)
                        .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.succeeded").value(1))
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.results[1].errorCode").value("ERR_PHONE_INVALID"));

        mockMvc.perform(get("/api/v1/farmers").header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].phone").doesNotExist());
    }

    private String officerToken(String username) throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/officer/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return token(login);
    }

    private String farmerToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"+8801700000001\"}"))
                .andExpect(status().isAccepted());
        MvcResult login = mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"+8801700000001\",\"code\":\"000000\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return token(login);
    }

    private static String token(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }
}

package com.rootcause.foshol.identity.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rootcause.foshol.common.contract.ErrorCodes;
import com.rootcause.foshol.common.cqrs.CommandBus;
import com.rootcause.foshol.common.cqrs.QueryBus;
import com.rootcause.foshol.identity.application.command.FarmerImportResult;
import com.rootcause.foshol.identity.application.command.RegisterFarmerResult;
import com.rootcause.foshol.identity.application.query.FarmerRecord;
import com.rootcause.foshol.identity.domain.IdentityException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class FarmerControllerWebMvcTest {

    private static final UUID OFFICER = UUID.fromString("01800000-0000-7000-8000-000000000301");
    private static final UUID FARMER = UUID.fromString("01800000-0000-7000-8000-000000000501");
    private static final Instant NOW = Instant.parse("2026-09-09T12:00:00Z");

    @Mock
    private CommandBus commands;

    @Mock
    private QueryBus queries;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FarmerController(commands, queries))
                .setControllerAdvice(new IdentityExceptionHandler())
                .build();
    }

    @Test
    void registerReturns201() throws Exception {
        FarmerRecord record = new FarmerRecord(
                FARMER, "Rahim", "DHK", "DHA", null, null, null, null, "bn", NOW, OFFICER, "IT Officer", "MANUAL");
        when(commands.handle(any())).thenReturn(new RegisterFarmerResult(record, false));
        mockMvc.perform(post("/api/v1/farmers")
                        .principal(auth())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"name\":\"Rahim\",\"phone\":\"+8801700000099\",\"divisionCode\":\"DHK\",\"districtCode\":\"DHA\",\"preferredLanguage\":\"bn\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Rahim"))
                .andExpect(jsonPath("$.phone").doesNotExist());
    }

    @Test
    void missingIdempotencyKeyIs400() throws Exception {
        when(commands.handle(any()))
                .thenThrow(new IdentityException(
                        ErrorCodes.ERR_IDEMPOTENCY_KEY_MISSING, 400, "Idempotency-Key is required."));
        mockMvc.perform(post("/api/v1/farmers")
                        .principal(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"name\":\"Rahim\",\"phone\":\"+8801700000099\",\"divisionCode\":\"DHK\",\"districtCode\":\"DHA\",\"preferredLanguage\":\"bn\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR_IDEMPOTENCY_KEY_MISSING"));
    }

    @Test
    void importReturnsPerRowResults() throws Exception {
        when(commands.handle(any()))
                .thenReturn(new FarmerImportResult(
                        1,
                        1,
                        List.of(
                                new FarmerImportResult.FarmerImportRowResult(2, "OK", FARMER, "Rahim", null, null),
                                new FarmerImportResult.FarmerImportRowResult(
                                        3, "FAILED", null, "Bad", "ERR_PHONE_INVALID", "Phone number is not valid."))));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "farmers.csv",
                "text/csv",
                "name,phone,divisionCode,districtCode,preferredLanguage\nRahim,+8801,DHK,DHA,bn\n".getBytes());
        mockMvc.perform(multipart("/api/v1/farmers/import").file(file).principal(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.succeeded").value(1))
                .andExpect(jsonPath("$.failed").value(1));
    }

    private static TestingAuthenticationToken auth() {
        TestingAuthenticationToken token = new TestingAuthenticationToken(OFFICER.toString(), "n", "ROLE_OFFICER");
        token.setAuthenticated(true);
        return token;
    }
}

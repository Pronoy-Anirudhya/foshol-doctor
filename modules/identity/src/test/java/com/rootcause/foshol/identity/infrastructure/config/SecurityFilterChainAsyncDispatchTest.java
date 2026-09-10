package com.rootcause.foshol.identity.infrastructure.config;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rootcause.foshol.common.contract.ErrorCodes;
import com.rootcause.foshol.common.enums.Role;
import com.rootcause.foshol.identity.infrastructure.adapter.JwtAuthenticationFilter;
import com.rootcause.foshol.identity.infrastructure.adapter.JwtService;
import com.rootcause.foshol.identity.infrastructure.adapter.ProblemAccessDeniedHandler;
import com.rootcause.foshol.identity.infrastructure.adapter.ProblemAuthenticationEntryPoint;
import com.rootcause.foshol.identity.infrastructure.adapter.SecurityProblemWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@SpringJUnitWebConfig(classes = SecurityFilterChainAsyncDispatchTest.TestConfig.class)
@TestPropertySource(properties = "foshol.web.cors.origins=http://localhost:4200")
class SecurityFilterChainAsyncDispatchTest {

    private static final UUID FARMER = UUID.fromString("01800000-0000-7000-8000-000000000501");

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JwtService jwt;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        SecurityContextHolder.clearContext();
        StubApiController.LAST.set(null);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void streamWithoutBearerIs401() throws Exception {
        mockMvc.perform(get("/api/v1/stream").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(containsString(ErrorCodes.ERR_TOKEN_INVALID)));
    }

    @Test
    void casesWithoutBearerIs401() throws Exception {
        mockMvc.perform(get("/api/v1/cases"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(containsString(ErrorCodes.ERR_TOKEN_INVALID)));
    }

    @Test
    void authenticatedStreamStartsAsync() throws Exception {
        String token = jwt.issue(FARMER, Role.FARMER, Instant.now()).compact();
        mockMvc.perform(get("/api/v1/stream")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andExpect(status().isOk());
    }

    @Test
    void asyncDispatchAfterThreadLocalClearedDoesNotFailAuth() throws Exception {
        String token = jwt.issue(FARMER, Role.FARMER, Instant.now()).compact();
        MvcResult started = mockMvc.perform(get("/api/v1/stream")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();
        SecurityContextHolder.clearContext();
        StubApiController.LAST.get().complete();
        mockMvc.perform(asyncDispatch(started)).andExpect(status().isOk());
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({
        SecurityConfiguration.class,
        JwtAuthenticationFilter.class,
        SecurityProblemWriter.class,
        ProblemAuthenticationEntryPoint.class,
        ProblemAccessDeniedHandler.class
    })
    static class TestConfig {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        JwtService jwtService() {
            JwtService service =
                    new JwtService("foshol-doctor", Duration.ofHours(8), "local-dev-jwt-secret-must-be-32chars");
            service.validateSecret();
            return service;
        }

        @Bean
        StubApiController stubApiController() {
            return new StubApiController();
        }
    }

    @RestController
    static class StubApiController {

        static final AtomicReference<SseEmitter> LAST = new AtomicReference<>();

        @GetMapping(path = "/api/v1/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        SseEmitter stream() {
            SseEmitter emitter = new SseEmitter(5_000L);
            LAST.set(emitter);
            return emitter;
        }

        @GetMapping("/api/v1/cases")
        Map<String, String> cases() {
            return Map.of("status", "ok");
        }
    }
}

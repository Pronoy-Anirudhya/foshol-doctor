package com.rootcause.foshol.analysis.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rootcause.foshol.analysis.application.command.LookupVoiceKbCommand;
import com.rootcause.foshol.analysis.application.command.VoiceKbDiseaseCandidate;
import com.rootcause.foshol.analysis.application.command.VoiceKbLookupResult;
import com.rootcause.foshol.common.cqrs.CommandBus;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class FaqVoiceControllerTest {

    static final UUID CROP = UUID.fromString("01800000-0000-7000-8000-000000000001");
    static final UUID BLAST = UUID.fromString("01800000-0000-7000-8000-000000000103");
    static final UUID FARMER = UUID.fromString("01800000-0000-7000-8000-000000000501");

    @Mock
    CommandBus commands;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FaqVoiceController(commands))
                .setControllerAdvice(new AnalysisExceptionHandler())
                .build();
    }

    @Test
    void returnsCandidatesWithoutRemedies() throws Exception {
        when(commands.handle(any(LookupVoiceKbCommand.class)))
                .thenReturn(new VoiceKbLookupResult(
                        "blast",
                        new BigDecimal("0.9100"),
                        List.of(new VoiceKbDiseaseCandidate(
                                BLAST, "BLAST", "ব্লাস্ট", "Blast", new BigDecimal("1.000"), "NAME")),
                        false));
        mockMvc.perform(multipart("/api/v1/faq/voice-search")
                        .file(audio())
                        .param("cropId", CROP.toString())
                        .param("preferred_language", "bn")
                        .principal(farmer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transcription").value("blast"))
                .andExpect(jsonPath("$.inconclusive").value(false))
                .andExpect(jsonPath("$.candidates[0].diseaseId").value(BLAST.toString()))
                .andExpect(jsonPath("$.candidates[0].matcher").value("NAME"))
                .andExpect(jsonPath("$.candidates[0].nameEn").value("Blast"))
                .andExpect(jsonPath("$.candidates[0].nameEnFallback").value(false))
                .andExpect(jsonPath("$.remedy").doesNotExist());
    }

    @Test
    void emptyAudioIs422() throws Exception {
        when(commands.handle(any(LookupVoiceKbCommand.class)))
                .thenThrow(new com.rootcause.foshol.analysis.domain.AnalysisException(
                        "ERR_AUDIO_UNREADABLE", 422, "Audio was empty or unreadable."));
        mockMvc.perform(multipart("/api/v1/faq/voice-search")
                        .file(new MockMultipartFile("audio", "silent.wav", "audio/wav", new byte[0]))
                        .param("cropId", CROP.toString())
                        .principal(farmer()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ERR_AUDIO_UNREADABLE"));
    }

    private static MockMultipartFile audio() {
        return new MockMultipartFile("audio", "clip.wav", "audio/wav", new byte[] {1, 2, 3, 4});
    }

    private static TestingAuthenticationToken farmer() {
        TestingAuthenticationToken auth =
                new TestingAuthenticationToken(FARMER.toString(), "n", "ROLE_FARMER");
        auth.setAuthenticated(true);
        return auth;
    }
}

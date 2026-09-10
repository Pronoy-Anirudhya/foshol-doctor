package com.rootcause.foshol.analysis.web;

import com.rootcause.foshol.analysis.application.command.LookupVoiceKbCommand;
import com.rootcause.foshol.analysis.application.command.VoiceKbDiseaseCandidate;
import com.rootcause.foshol.analysis.application.command.VoiceKbLookupResult;
import com.rootcause.foshol.common.CorrelationId;
import com.rootcause.foshol.common.cqrs.CommandBus;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/faq")
@PreAuthorize("hasRole('FARMER')")
public class FaqVoiceController {

    private final CommandBus commands;

    public FaqVoiceController(CommandBus commands) {
        this.commands = commands;
    }

    @PostMapping(path = "/voice-search", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public VoiceSearchResponse voiceSearch(
            @RequestParam UUID cropId,
            @RequestParam(name = "preferred_language", defaultValue = "bn") String preferredLanguage,
            @RequestPart("audio") MultipartFile audio)
            throws IOException {
        byte[] bytes = audio == null ? new byte[0] : audio.getBytes();
        String correlationId = CorrelationId.currentOrCreate();
        VoiceKbLookupResult result = commands.handle(new LookupVoiceKbCommand(
                cropId, bytes, preferredLanguage, correlationId));
        return toResponse(result);
    }

    private static VoiceSearchResponse toResponse(VoiceKbLookupResult result) {
        List<VoiceSearchCandidateResponse> candidates = new ArrayList<>();
        for (VoiceKbDiseaseCandidate candidate : result.candidates()) {
            candidates.add(new VoiceSearchCandidateResponse(
                    candidate.diseaseId(),
                    candidate.code(),
                    candidate.nameBn(),
                    candidate.score(),
                    candidate.matcher()));
        }
        return new VoiceSearchResponse(
                result.transcription(), result.asrConfidence(), List.copyOf(candidates), result.inconclusive());
    }
}

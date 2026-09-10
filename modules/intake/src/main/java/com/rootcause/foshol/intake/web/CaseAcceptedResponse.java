package com.rootcause.foshol.intake.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CaseAcceptedResponse(UUID caseId, String status, String submittedAt) {

    public static CaseAcceptedResponse submitted(UUID caseId, String submittedAt) {
        return new CaseAcceptedResponse(caseId, "SUBMITTED", submittedAt);
    }
}

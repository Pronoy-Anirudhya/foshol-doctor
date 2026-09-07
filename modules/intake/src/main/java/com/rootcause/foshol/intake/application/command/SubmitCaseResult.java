package com.rootcause.foshol.intake.application.command;

import java.util.UUID;

public record SubmitCaseResult(UUID caseId, boolean replayed, String body) {

    public static SubmitCaseResult accepted(UUID caseId, String body) {
        return new SubmitCaseResult(caseId, false, body);
    }

    public static SubmitCaseResult replay(String body) {
        String marker = "\"caseId\":\"";
        int start = body.indexOf(marker) + marker.length();
        int end = body.indexOf('"', start);
        return new SubmitCaseResult(UUID.fromString(body.substring(start, end)), true, body);
    }
}

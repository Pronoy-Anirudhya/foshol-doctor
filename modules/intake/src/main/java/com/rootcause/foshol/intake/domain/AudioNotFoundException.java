package com.rootcause.foshol.intake.domain;

import com.rootcause.foshol.intake.domain.vo.CaseId;

public class AudioNotFoundException extends RuntimeException {

    private final CaseId caseId;

    public AudioNotFoundException(CaseId caseId) {
        super("audio not found for case: " + caseId);
        this.caseId = caseId;
    }

    public CaseId caseId() {
        return caseId;
    }
}

package com.rootcause.foshol.intake.domain;

import com.rootcause.foshol.intake.domain.vo.CaseId;

public class CaseNotFoundException extends RuntimeException {

    private final CaseId caseId;

    public CaseNotFoundException(CaseId caseId) {
        super("case not found: " + caseId);
        this.caseId = caseId;
    }

    public CaseId caseId() {
        return caseId;
    }
}

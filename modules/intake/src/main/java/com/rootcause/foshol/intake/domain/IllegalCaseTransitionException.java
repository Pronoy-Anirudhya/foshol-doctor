package com.rootcause.foshol.intake.domain;

import com.rootcause.foshol.intake.domain.vo.CaseId;
import com.rootcause.foshol.common.CaseStatus;

public class IllegalCaseTransitionException extends RuntimeException {

    private final CaseId caseId;
    private final CaseStatus fromStatus;
    private final CaseStatus toStatus;
    private final String correlationId;

    public IllegalCaseTransitionException(
            CaseId caseId, CaseStatus fromStatus, CaseStatus toStatus, String correlationId) {
        super("illegal transition " + fromStatus + " -> " + toStatus + " for case " + caseId);
        this.caseId = caseId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.correlationId = correlationId;
    }

    public CaseId caseId() {
        return caseId;
    }

    public CaseStatus fromStatus() {
        return fromStatus;
    }

    public CaseStatus toStatus() {
        return toStatus;
    }

    public String correlationId() {
        return correlationId;
    }
}

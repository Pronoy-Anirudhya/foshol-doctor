package com.rootcause.foshol.intake.domain.spec;

import com.rootcause.foshol.common.CaseStatus;
import com.rootcause.foshol.intake.domain.DiagnosisCase;
import java.util.UUID;

public final class ResubmissionParentSpec {

    public static final ResubmissionParentSpec INSTANCE = new ResubmissionParentSpec();

    private ResubmissionParentSpec() {}

    public boolean isSatisfied(DiagnosisCase parent, UUID farmerId) {
        if (parent == null || farmerId == null) {
            return false;
        }
        return parent.farmerId().equals(farmerId) && parent.status() == CaseStatus.REJECTED;
    }
}

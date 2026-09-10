package com.rootcause.foshol.intake.domain.spec;

import com.rootcause.foshol.common.enums.CaseStatus;
import java.util.Set;

public final class TransitionAllowedSpec {

    public static final TransitionAllowedSpec INSTANCE = new TransitionAllowedSpec();

    private static final Set<Transition> ALLOWED = Set.of(
            new Transition(CaseStatus.SUBMITTED, CaseStatus.ANALYSING),
            new Transition(CaseStatus.ANALYSING, CaseStatus.ANALYSED),
            new Transition(CaseStatus.ANALYSED, CaseStatus.IN_REVIEW),
            new Transition(CaseStatus.ANALYSING, CaseStatus.FAILED),
            new Transition(CaseStatus.IN_REVIEW, CaseStatus.ADVISED),
            new Transition(CaseStatus.IN_REVIEW, CaseStatus.REJECTED),
            new Transition(CaseStatus.FAILED, CaseStatus.ADVISED),
            new Transition(CaseStatus.FAILED, CaseStatus.REJECTED));

    private TransitionAllowedSpec() {}

    public boolean isSatisfied(CaseStatus from, CaseStatus to) {
        if (from == null || to == null) {
            return false;
        }
        return ALLOWED.contains(new Transition(from, to));
    }

    private record Transition(CaseStatus from, CaseStatus to) {}
}
